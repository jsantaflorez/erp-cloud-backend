package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.SiewinBalanceImportRequest;
import com.erp.erp_cloud.dto.SiewinBalanceImportResultDTO;
import com.erp.erp_cloud.dto.SiewinBalanceRowDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Importador de saldos de apertura desde el plano de SIEWIN (RPV v.6).
 *
 * Implementa el diseño aprobado por el usuario en
 * claude/analisis-migracion-saldos-siewin-2026-11-12.md:
 *  - Sección 1.2: un único JournalEntry "APERTURA" por compañía, con un
 *    JournalEntryItem por cada cuenta imputable (hoja) x tercero.
 *  - Sección 1.3: las cuentas de Ingresos/Costos/Gastos (clases 4, 5, 6)
 *    nunca llegan en el archivo -- el resultado neto del ejercicio se
 *    recalcula como la suma de todas las líneas hoja incluidas (Activo +
 *    Pasivo + Patrimonio, convención "débito menos crédito" cruda, igual
 *    que AccountingPeriodService.buildAndSaveClosingEntry()) y se postea
 *    como una única línea de contrapartida contra
 *    Company.defaultGainAccount (positivo = utilidad, crédito) o
 *    Company.defaultLossAccount (negativo = pérdida, débito).
 *  - Sección 1.4: cualquier cuenta con requiresCostCenter=true y saldo
 *    distinto de cero rechaza TODO el archivo -- los centros de costo
 *    quedaron explícitamente fuera de alcance.
 *  - Sección 2.2/2.5: reemplazo total vía neutralización (nunca upsert
 *    parcial) del comprobante APERTURA anterior, con los chequeos de
 *    seguridad aprobados (año/periodo cerrado en el SaaS, más de un
 *    comprobante de apertura vivo) antes de reemplazar nada.
 *  - Sección 3.3: validación Fail-Fast -- se acumulan TODAS las
 *    violaciones encontradas y se lanza una sola InvalidOperationException
 *    con el detalle completo, nunca se falla en el primer error.
 *  - Sección 6.2/6.3: deduplicación de bloques de filas exactamente
 *    repetidos (bug conocido de SIEWIN RPV v.6) y clasificación de cada
 *    fila cruzando contra el PUC/Terceros ya migrados de la compañía, en
 *    vez de adivinar por la forma del código.
 */
@Service
@RequiredArgsConstructor
public class SiewinBalanceImportService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(SiewinBalanceImportService.class);

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // Forma de un código de cuenta del PUC de este cliente: una raíz de 1-2
    // dígitos seguida de hasta 4 segmentos de 2 dígitos ("11", "23-65",
    // "23-65-40-01") -- el mismo convenio de +2 dígitos por nivel ya usado
    // por el importador de PUC (SiewinPucImportService). Un número de
    // documento de tercero nunca calza aquí (es una sola corrida de
    // dígitos, sin guiones).
    // UPDATE 2026-11-17: SIEWIN imprime del nivel 4 en adelante con hasta 4
    // dígitos ("22-05-05-9999", "11-05-05-0001"); se aceptan tramos de 2 a
    // 4 dígitos y la conversión al código del ERP la hace SiewinCodes (la
    // MISMA regla del importador de PUC, incluido 9999 -> 99).
    private static final Pattern ACCOUNT_CODE_SHAPE = Pattern.compile("\\d{1,2}(-\\d{2,4}){0,5}");

    private final ChartOfAccountsRepository accountRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final DocumentTypeService documentTypeService;
    private final AccountingPeriodService accountingPeriodService;
    private final EntityManager entityManager;

    /**
     * NEW (2026-11-18): the company's current live APERTURA entry, if any,
     * so the migration screen can warn that migrating again replaces it
     * completely (manual edits included). More than one live entry is a
     * data problem importFromSiewin() already rejects; here the first is
     * reported, the screen only needs to know one exists.
     */
    @Transactional(readOnly = true)
    public com.erp.erp_cloud.dto.OpeningBalanceStatusDTO currentOpeningStatus() {
        List<JournalEntry> live = journalEntryRepository.findLiveByCompanyIdAndDocumentTypeCode(
                currentTenantId(), DocumentTypeService.OPENING_DOC_TYPE_CODE);
        if (live.isEmpty()) {
            return com.erp.erp_cloud.dto.OpeningBalanceStatusDTO.none();
        }
        JournalEntry e = live.get(0);
        return new com.erp.erp_cloud.dto.OpeningBalanceStatusDTO(
                true, e.getDocumentNumber(), e.getEntryDate(), e.getItems() == null ? 0 : e.getItems().size());
    }

    @Transactional
    public SiewinBalanceImportResultDTO importFromSiewin(SiewinBalanceImportRequest request, String performedBy) {
        Long companyId = currentTenantId();
        Company company = currentCompany();
        if (company == null) {
            throw new InvalidOperationException("No se encontró la compañía actual.", "COMPANY_NOT_FOUND");
        }
        if (request == null || request.closingYear() == null) {
            throw new InvalidOperationException(
                    "Debe indicar el año de cierre que se está migrando.", "SIEWIN_BALANCE_MISSING_YEAR");
        }
        List<SiewinBalanceRowDTO> rawRows = request.rows();
        if (rawRows == null || rawRows.isEmpty()) {
            throw new InvalidOperationException(
                    "El archivo no contiene filas para importar.", "SIEWIN_BALANCE_EMPTY_FILE");
        }

        LocalDate openingDate = LocalDate.of(request.closingYear(), 12, 31);

        // Chequeos de seguridad (sección 2.5, aprobados por el usuario):
        // se reutilizan los mismos códigos que ya usa el resto del sistema
        // para "este periodo/año está bloqueado" en vez de inventar unos
        // nuevos -- si ya corrió un Cierre de Año real para esta compañía
        // y año dentro del SaaS, o diciembre está bloqueado por otra
        // razón, esta importación no debe pasar por encima en silencio.
        accountingPeriodService.validateDateIsOpen(openingDate, companyId);

        List<JournalEntry> liveOpeningEntries = journalEntryRepository.findLiveByCompanyIdAndDocumentTypeCode(
                companyId, DocumentTypeService.OPENING_DOC_TYPE_CODE);
        if (liveOpeningEntries.size() > 1) {
            throw new InvalidOperationException(
                    "Existe más de un comprobante de apertura vivo para esta compañía. Contacte soporte antes de continuar.",
                    "SIEWIN_BALANCE_MULTIPLE_OPENING_ENTRIES_FOUND");
        }

        // Paso 0: eliminar bloques de filas exactamente duplicados
        // (sección 6.2 -- defensa adicional; el chequeo de jerarquía más
        // abajo también atraparía esto vía SIEWIN_BALANCE_OUT_OF_BALANCE,
        // pero esto es económico y quita la ambigüedad desde el inicio).
        DedupResult dedup = removeDuplicateBlocks(rawRows);
        List<SiewinBalanceRowDTO> rows = dedup.rows();

        // Paso 1: clasificar cada fila cruzando contra los catálogos ya
        // migrados de la compañía (nunca adivinando solo por la forma del
        // código -- sección 6.3) y acumular cada línea hoja a postear.
        Map<String, ChartOfAccounts> accountsByCode = accountRepository
                .findByCompanyIdAndActiveTrueOrderByCodeAsc(companyId)
                .stream()
                .collect(Collectors.toMap(ChartOfAccounts::getCode, a -> a, (a, b) -> a));

        List<String> accountNotFound = new ArrayList<>();
        List<String> accountInactive = new ArrayList<>();
        List<String> thirdPartyNotFound = new ArrayList<>();
        List<String> costCenterNotSupported = new ArrayList<>();
        List<String> thirdPartyMismatch = new ArrayList<>();
        List<String> unexpectedThirdParty = new ArrayList<>();
        List<String> duplicateLeafLines = new ArrayList<>();

        Map<String, BigDecimal> printedGroupTotals = new LinkedHashMap<>();
        Map<String, LeafPosting> leafPostings = new LinkedHashMap<>();
        Map<String, ThirdParty> thirdPartyCache = new HashMap<>();

        PendingLeaf pending = null;

        for (SiewinBalanceRowDTO row : rows) {
            String code = row.rawCode() == null ? "" : row.rawCode().trim();
            if (code.isEmpty() || code.toUpperCase(Locale.ROOT).startsWith("TOTAL")) {
                continue; // defensivo -- el frontend ya debería haberlas descartado (sección 6.3)
            }
            BigDecimal amount = row.amount() == null ? ZERO : row.amount().setScale(2, RoundingMode.HALF_UP);

            boolean looksLikeAccountCode = ACCOUNT_CODE_SHAPE.matcher(code).matches();

            if (looksLikeAccountCode) {
                flushPending(pending, thirdPartyMismatch, leafPostings, duplicateLeafLines);
                pending = null;

                // FIX (2026-11-17): el archivo imprime el código con guiones
                // ("11-05-05-01") pero el PUC migrado por SiewinPucImportService
                // lo guarda solo con dígitos ("11050501", un tramo de 2 dígitos
                // por nivel). Sin quitar los guiones ninguna cuenta calzaba y,
                // en cascada, todos los terceros salían "sin una cuenta asociada".
                // UPDATE 2026-11-17: misma conversión que el importador de PUC
                // (tramos leídos como número, 9999 -> 99), vía SiewinCodes.
                String accountCode = SiewinCodes.erpCodeFromDashed(code);
                if (accountCode == null) {
                    accountNotFound.add("fila " + row.rowIndex() + ": " + code + " - " + row.name()
                            + " (codigo no representable en el ERP)");
                    continue;
                }
                ChartOfAccounts account = accountsByCode.get(accountCode);
                if (account == null) {
                    // NEW (2026-11-17): distinguir "no existe" de "existe pero
                    // está inactiva" -- la segunda se arregla reactivándola en
                    // Plan de Cuentas, no creándola (se crearía duplicada).
                    boolean inactive = accountRepository.findByCompanyIdAndCode(companyId, accountCode)
                            .map(a -> !a.isActive())
                            .orElse(false);
                    if (inactive) {
                        accountInactive.add("fila " + row.rowIndex() + ": " + code + " - " + row.name());
                    } else {
                        accountNotFound.add("fila " + row.rowIndex() + ": " + code + " - " + row.name());
                    }
                    continue;
                }

                printedGroupTotals.put(accountCode, amount);

                if (!account.isPostingAccount()) {
                    continue; // cuenta de grupo/encabezado -- solo sirve para el chequeo de jerarquía
                }
                if (account.isRequiresCostCenter() && amount.compareTo(BigDecimal.ZERO) != 0) {
                    costCenterNotSupported.add(code + " - " + account.getName() + " (saldo " + amount + ")");
                    continue;
                }
                pending = new PendingLeaf(account, amount);
                continue;
            }

            // Fila que no tiene forma de código de cuenta: es un desglose
            // de tercero, anidado bajo la última cuenta hoja vista.
            if (pending == null) {
                thirdPartyNotFound.add("fila " + row.rowIndex() + ": " + code + " - " + row.name()
                        + " (sin una cuenta asociada)");
                continue;
            }
            if (!pending.account.isRequiresThirdParty()) {
                unexpectedThirdParty.add(pending.account.getCode() + " - " + pending.account.getName()
                        + ": trae desglose de terceros pero la cuenta no lo requiere (fila " + row.rowIndex()
                        + ": " + code + ")");
                continue;
            }
            ThirdParty tp = thirdPartyCache.computeIfAbsent(code,
                    doc -> thirdPartyRepository.findByCompanyIdAndDocumentNumber(companyId, doc).orElse(null));
            if (tp == null) {
                thirdPartyNotFound.add("fila " + row.rowIndex() + ": " + code + " - " + row.name());
                continue;
            }
            pending.thirdPartyLines.add(new ThirdPartyAmount(tp, amount));
            pending.thirdPartySum = pending.thirdPartySum.add(amount);
        }
        flushPending(pending, thirdPartyMismatch, leafPostings, duplicateLeafLines);

        if (!accountNotFound.isEmpty() || !accountInactive.isEmpty() || !thirdPartyNotFound.isEmpty() || !costCenterNotSupported.isEmpty()
                || !thirdPartyMismatch.isEmpty() || !unexpectedThirdParty.isEmpty() || !duplicateLeafLines.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("accountNotFound", accountNotFound);
            details.put("accountInactive", accountInactive);
            details.put("thirdPartyNotFound", thirdPartyNotFound);
            details.put("costCenterNotSupported", costCenterNotSupported);
            details.put("thirdPartyMismatch", thirdPartyMismatch);
            details.put("unexpectedThirdParty", unexpectedThirdParty);
            details.put("duplicateLeafLines", duplicateLeafLines);
            log.warn("SIEWIN_BALANCE_IMPORT_INTEGRITY_GAP | company: {} | details: {}", companyId, details);
            throw new InvalidOperationException(
                    "El archivo de saldos tiene problemas que impiden la importación. Revise los detalles.",
                    "SIEWIN_BALANCE_IMPORT_INTEGRITY_GAP", details);
        }

        // Paso 2: chequeo de jerarquía -- cada subtotal que el archivo
        // imprime debe ser igual a la suma de las hojas que vamos a
        // postear debajo de él (sección 6.2: los subtotales impresos por
        // SIEWIN se mantienen correctos aun cuando filas de detalle salen
        // duplicadas, así que este chequeo por sí solo habría atrapado
        // ese bug incluso sin la deduplicación explícita del paso 0).
        List<String> outOfBalance = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> group : printedGroupTotals.entrySet()) {
            String groupCode = group.getKey();
            BigDecimal printed = group.getValue();
            BigDecimal recomputed = leafPostings.values().stream()
                    // Códigos solo con dígitos: las hojas de un grupo son las
                    // que empiezan por su código (mismo criterio jerárquico
                    // del resto del sistema).
                    .filter(lp -> lp.account.getCode().startsWith(groupCode))
                    .map(LeafPosting::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            if (recomputed.compareTo(printed) != 0) {
                outOfBalance.add(groupCode + ": impreso " + printed + ", recalculado " + recomputed);
            }
        }
        if (!outOfBalance.isEmpty()) {
            throw new InvalidOperationException(
                    "Los subtotales del archivo no cuadran contra las cuentas hoja recalculadas. Revise los detalles.",
                    "SIEWIN_BALANCE_OUT_OF_BALANCE", outOfBalance);
        }

        // Resultado neto (las cuentas de Ingresos/Gastos/Costos entran en
        // cero -- sección 1.3): el archivo las excluye por completo, así
        // que la única línea de contrapartida necesaria para que el
        // comprobante cuadre es el residuo de todo lo demás, con la misma
        // convención de signo que AccountingPeriodService
        // .buildAndSaveClosingEntry(): positivo = Utilidad (crédito a
        // defaultGainAccount), negativo = Pérdida (débito a
        // defaultLossAccount).
        BigDecimal netResult = leafPostings.values().stream()
                .map(LeafPosting::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (leafPostings.isEmpty() && netResult.compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidOperationException(
                    "El archivo no generó ninguna línea de apertura con saldo. Revise el archivo.",
                    "SIEWIN_BALANCE_NO_LINES_TO_POST");
        }

        ChartOfAccounts gainAccount = company.getDefaultGainAccount();
        ChartOfAccounts lossAccount = company.getDefaultLossAccount();
        if (netResult.compareTo(BigDecimal.ZERO) > 0 && gainAccount == null) {
            throw new InvalidOperationException(
                    "La compañía no tiene configurada su Cuenta de Ganancia por Defecto; configúrela antes de importar saldos de apertura.",
                    "SIEWIN_BALANCE_CLOSING_ACCOUNTS_NOT_CONFIGURED");
        }
        if (netResult.compareTo(BigDecimal.ZERO) < 0 && lossAccount == null) {
            throw new InvalidOperationException(
                    "La compañía no tiene configurada su Cuenta de Pérdida por Defecto; configúrela antes de importar saldos de apertura.",
                    "SIEWIN_BALANCE_CLOSING_ACCOUNTS_NOT_CONFIGURED");
        }

        // Todo validado: reemplazar el comprobante de apertura anterior
        // (si existe) y postear el nuevo.
        boolean replacedExistingEntry = !liveOpeningEntries.isEmpty();
        if (replacedExistingEntry) {
            annulOpeningEntry(liveOpeningEntries.get(0), performedBy, request.closingYear());
        }

        JournalEntry entry = buildOpeningEntry(companyId, request.closingYear(), leafPostings.values(),
                netResult, gainAccount, lossAccount);
        JournalEntry saved = journalEntryRepository.save(entry);

        int thirdPartyLines = (int) leafPostings.values().stream().filter(lp -> lp.thirdParty != null).count();
        int accountOnlyLines = leafPostings.size() - thirdPartyLines;

        log.info("Importación de saldos SIEWIN: compañía {} año {} -> comprobante {} ({} líneas de cuenta, "
                        + "{} líneas de tercero, resultado neto {}, reemplazó comprobante anterior={})",
                companyId, request.closingYear(), saved.getDocumentNumber(), accountOnlyLines, thirdPartyLines,
                netResult, replacedExistingEntry);

        return new SiewinBalanceImportResultDTO(
                saved.getDocumentNumber(), accountOnlyLines, thirdPartyLines, netResult,
                replacedExistingEntry, dedup.removedBlockDescriptions());
    }

    private void flushPending(PendingLeaf pending, List<String> thirdPartyMismatch,
                               Map<String, LeafPosting> leafPostings, List<String> duplicateLeafLines) {
        if (pending == null) {
            return;
        }
        if (pending.account.isRequiresThirdParty()) {
            if (pending.thirdPartyLines.isEmpty()) {
                if (pending.printedAmount.compareTo(BigDecimal.ZERO) != 0) {
                    thirdPartyMismatch.add(pending.account.getCode() + " - " + pending.account.getName()
                            + ": requiere desglose de terceros y no trae ninguno (saldo impreso "
                            + pending.printedAmount + ")");
                }
                return;
            }
            if (pending.thirdPartySum.setScale(2, RoundingMode.HALF_UP).compareTo(pending.printedAmount) != 0) {
                thirdPartyMismatch.add(pending.account.getCode() + " - " + pending.account.getName()
                        + ": el desglose de terceros suma " + pending.thirdPartySum
                        + " pero la cuenta imprime " + pending.printedAmount);
                return;
            }
            // FIX (2026-11-17): dentro del bloque de UNA cuenta, SIEWIN puede
            // imprimir el mismo tercero en varios renglones con valores
            // distintos (caso real Navarra, 23-65-70-01: NAVARRA 8 veces,
            // OCTAVIO RIOS 6...). No son duplicados: la suma de TODOS los
            // renglones es exactamente el saldo de la cuenta (ya verificado
            // arriba), así que se suman por tercero. Un renglón repetido por
            // el bug de bloques de SIEWIN no llega aquí (lo quita el paso 0),
            // y la misma cuenta+tercero en DOS bloques distintos de cuenta
            // sigue reportándose como duplicateLeafLines.
            Map<Long, ThirdPartyAmount> byThirdParty = new LinkedHashMap<>();
            for (ThirdPartyAmount tpa : pending.thirdPartyLines) {
                byThirdParty.merge(tpa.thirdParty().getId(), tpa,
                        (a, b) -> new ThirdPartyAmount(a.thirdParty(), a.amount().add(b.amount())));
            }
            for (ThirdPartyAmount tpa : byThirdParty.values()) {
                if (tpa.amount().compareTo(BigDecimal.ZERO) == 0) {
                    continue; // renglones que se anulan entre sí: el tercero queda sin saldo
                }
                addLeaf(leafPostings, duplicateLeafLines, pending.account, tpa.thirdParty(),
                        tpa.amount().setScale(2, RoundingMode.HALF_UP));
            }
        } else if (pending.printedAmount.compareTo(BigDecimal.ZERO) != 0) {
            addLeaf(leafPostings, duplicateLeafLines, pending.account, null, pending.printedAmount);
        }
    }

    private void addLeaf(Map<String, LeafPosting> leafPostings, List<String> duplicateLeafLines,
                          ChartOfAccounts account, ThirdParty thirdParty, BigDecimal amount) {
        String key = account.getId() + "|" + (thirdParty != null ? thirdParty.getId() : "");
        if (leafPostings.containsKey(key)) {
            duplicateLeafLines.add(account.getCode() + " - " + account.getName()
                    + (thirdParty != null ? " / " + thirdParty.getDocumentNumber() : "")
                    + ": aparece más de una vez como línea final distinta tras la deduplicación de bloques.");
            return;
        }
        leafPostings.put(key, new LeafPosting(account, thirdParty, amount));
    }

    private DedupResult removeDuplicateBlocks(List<SiewinBalanceRowDTO> rawRows) {
        // Igual al detector genérico de bloques contiguos repetidos usado
        // para confirmar este bug exacto contra el archivo real de
        // Navarra (sección 6.2): se intentan primero los bloques más
        // largos para que un bloque de 7 filas no se reporte como tres
        // coincidencias sueltas de 2-3 filas.
        List<SiewinBalanceRowDTO> rows = new ArrayList<>(rawRows);
        List<String> removed = new ArrayList<>();
        int n = rows.size();
        boolean[] dropped = new boolean[n];
        int maxBlockLen = Math.min(50, n / 2);
        for (int blockLen = maxBlockLen; blockLen >= 2; blockLen--) {
            int i = 0;
            while (i + 2 * blockLen <= n) {
                if (dropped[i]) {
                    i++;
                    continue;
                }
                boolean match = true;
                for (int k = 0; k < blockLen; k++) {
                    if (dropped[i + k] || dropped[i + blockLen + k]
                            || !sameRow(rows.get(i + k), rows.get(i + blockLen + k))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    for (int k = 0; k < blockLen; k++) {
                        dropped[i + blockLen + k] = true;
                    }
                    removed.add("filas " + rows.get(i).rowIndex() + "-" + rows.get(i + blockLen - 1).rowIndex()
                            + " duplicadas en filas " + rows.get(i + blockLen).rowIndex() + "-"
                            + rows.get(i + 2 * blockLen - 1).rowIndex() + " (" + blockLen + " filas)");
                    i += 2 * blockLen;
                } else {
                    i++;
                }
            }
        }
        List<SiewinBalanceRowDTO> kept = new ArrayList<>();
        for (int idx = 0; idx < n; idx++) {
            if (!dropped[idx]) {
                kept.add(rows.get(idx));
            }
        }
        return new DedupResult(kept, removed);
    }

    private boolean sameRow(SiewinBalanceRowDTO a, SiewinBalanceRowDTO b) {
        return Objects.equals(a.rawCode(), b.rawCode())
                && Objects.equals(a.name(), b.name())
                && Objects.equals(normalizeAmount(a.amount()), normalizeAmount(b.amount()));
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void annulOpeningEntry(JournalEntry entry, String performedBy, Integer closingYear) {
        entry.setAnnulled(true);
        entry.setAnnulledAt(LocalDateTime.now());
        entry.setAnnulledBy(performedBy);
        entry.setAnnulmentReason("Reemplazado por una nueva carga de saldos SIEWIN para el año " + closingYear);
        for (JournalEntryItem item : entry.getItems()) {
            item.setDebit(ZERO);
            item.setCredit(ZERO);
        }
        journalEntryRepository.save(entry);
    }

    private JournalEntry buildOpeningEntry(Long companyId, Integer closingYear, Collection<LeafPosting> leaves,
                                            BigDecimal netResult, ChartOfAccounts gainAccount,
                                            ChartOfAccounts lossAccount) {
        DocumentType docType = documentTypeService.findOrCreateOpeningDocumentType(companyId);

        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(LocalDate.of(closingYear, 12, 31));
        entry.setDescription("Apertura de Saldos (Migración SIEWIN) " + closingYear);
        entry.setCompany(entityManager.getReference(Company.class, companyId));

        Long nextNumber = documentTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        entry.setDocumentNumber(
                (docType.getPrefix() != null && !docType.getPrefix().isBlank())
                        ? docType.getPrefix().trim() + "-" + nextNumber
                        : nextNumber.toString());

        for (LeafPosting leaf : leaves) {
            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(entityManager.getReference(ChartOfAccounts.class, leaf.account().getId()));
            BigDecimal amount = leaf.amount();
            if (amount.compareTo(BigDecimal.ZERO) >= 0) {
                item.setDebit(amount);
                item.setCredit(ZERO);
            } else {
                item.setDebit(ZERO);
                item.setCredit(amount.negate());
            }
            if (leaf.thirdParty() != null) {
                item.setThirdParty(entityManager.getReference(ThirdParty.class, leaf.thirdParty().getId()));
            }
            item.setDescription("Apertura " + closingYear + " - " + leaf.account().getCode());
            entry.addItem(item);
        }

        if (netResult.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntryItem resultItem = new JournalEntryItem();
            resultItem.setAccount(entityManager.getReference(ChartOfAccounts.class, gainAccount.getId()));
            resultItem.setDebit(ZERO);
            resultItem.setCredit(netResult);
            resultItem.setDescription("Utilidad del Ejercicio " + closingYear + " (migración SIEWIN)");
            entry.addItem(resultItem);
        } else if (netResult.compareTo(BigDecimal.ZERO) < 0) {
            JournalEntryItem resultItem = new JournalEntryItem();
            resultItem.setAccount(entityManager.getReference(ChartOfAccounts.class, lossAccount.getId()));
            resultItem.setDebit(netResult.negate());
            resultItem.setCredit(ZERO);
            resultItem.setDescription("Pérdida del Ejercicio " + closingYear + " (migración SIEWIN)");
            entry.addItem(resultItem);
        }

        return entry;
    }

    private static final class PendingLeaf {
        final ChartOfAccounts account;
        final BigDecimal printedAmount;
        final List<ThirdPartyAmount> thirdPartyLines = new ArrayList<>();
        BigDecimal thirdPartySum = BigDecimal.ZERO;

        PendingLeaf(ChartOfAccounts account, BigDecimal printedAmount) {
            this.account = account;
            this.printedAmount = printedAmount;
        }
    }

    private record ThirdPartyAmount(ThirdParty thirdParty, BigDecimal amount) {
    }

    private record LeafPosting(ChartOfAccounts account, ThirdParty thirdParty, BigDecimal amount) {
    }

    private record DedupResult(List<SiewinBalanceRowDTO> rows, List<String> removedBlockDescriptions) {
    }
}
