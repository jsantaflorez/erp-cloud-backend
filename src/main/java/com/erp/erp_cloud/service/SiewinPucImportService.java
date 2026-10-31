package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.dto.SiewinPucImportRequest;
import com.erp.erp_cloud.dto.SiewinPucImportResultDTO;
import com.erp.erp_cloud.dto.SiewinPucRowDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.AccountNature;
import com.erp.erp_cloud.enums.FinancialStatement;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Importer for a client's legacy PUC coming from SIEWIN
 * ({@code t_puc_maestro26}), migrating it into real ChartOfAccounts rows
 * for the current tenant.
 *
 * Full analysis and the 5 approved decisions behind every choice below are
 * in the project doc propuesta-importador-puc-siewin-2026-10-31.md. Summary
 * of what was approved (2026-10-31, explicit user direction) and why:
 *
 * 1. DIGIT-JUMP STRATEGY -- SIEWIN's own level widths (nivel1..nivel6, each
 *    up to 2-4 digits) do NOT follow this system's strict "+2 digits per
 *    level" rule enforced by ChartOfAccountService.validateCodeStructure(),
 *    which is never touched or relaxed here (that rule protects every
 *    other account in the system, manual or templated). Instead, every
 *    populated level is re-derived with its OWN 2-digit zero-padded slot
 *    (code.append(String.format("%02d", level))), verified against the
 *    real 495-row sample: works losslessly for 494 of 495 rows.
 *
 * 2. The one row that doesn't fit (a level value > 99, e.g. the sample's
 *    "VARIOS" / nivel4=9999) is REJECTED explicitly -- never guessed,
 *    never silently truncated or remapped to a sentinel code of our own
 *    choosing. It surfaces in the outOfRangeLevels list of
 *    SIEWIN_IMPORT_INTEGRITY_GAP so the accountant can reclassify it in
 *    the source file before re-running the import. This also means the
 *    check is generic (any level > 99 on any future client's file), not a
 *    hardcoded exception for this one account.
 *
 * 3. INGESTION FORMAT -- the frontend parses the uploaded .xlsx/.csv (the
 *    `xlsx` library the frontend already depends on) and sends the rows
 *    here as plain JSON (SiewinPucRowDTO), untouched: no business logic
 *    runs in the browser, only cell extraction. Keeps the API JSON-only,
 *    no new file-upload infrastructure.
 *
 * 4. SCOPE -- requiresThirdParty (pucm_bare == 6) and requiresCostCenter
 *    (pucm_ceco != 0) ARE mapped in this version (see createOne()). Every
 *    other t_puc_maestro26 column was 100% zero/empty in the real sample
 *    and stays unmapped -- see SiewinPucRowDTO's javadoc.
 *
 * 5. PRECONDITION -- same as Fase 4's "Importar PUC Estandar": only
 *    allowed while ChartOfAccounts is completely empty for this company
 *    (CHART_OF_ACCOUNTS_NOT_EMPTY, same error code, reused on purpose).
 *    No merge-into-a-non-empty-plan mode exists.
 *
 * ---------------------------------------------------------------------
 * Reused from ChartAccountTemplateImportService without a redesign (see
 * that class for the original rationale): ChartOfAccountService.create()
 * in a loop so every structural rule it already enforces applies here
 * exactly as for a manually-typed account; 1-digit root synthesis
 * (ROOT_NAMES); strict ascending-code-length insertion order with a
 * codeToId map to resolve parentId; the rule-based nature resolver
 * ((DB)/(CR) markers, ASSET+"PROVISION"/accumulated-depreciation,
 * REVENUE/COST/EXPENSE+"DEVOLUCION"); and the "collect every violation,
 * throw ONE exception" fail-fast pattern -- here unified into a single
 * SIEWIN_IMPORT_INTEGRITY_GAP covering every validation category at once
 * (outOfRangeLevels, levelSkipGaps, duplicateCodes, orphanCodes,
 * postingAccountHasChildren, missingNames, unmappedGroups) rather than one
 * exception per rule, so a bad source file is fully diagnosed in a single
 * round trip.
 *
 * NOT reused/changed: Company.chartTemplate is left untouched by this
 * importer. SIEWIN's own group numbering doesn't correspond to either the
 * COMERCIAL or SOLIDARIO reference template (see the category map below),
 * so this company has no applicable ChartTemplateType value -- it simply
 * stays null, exactly as ChartTemplateType's own javadoc says a custom-
 * scheme entity should be. The "Importar PUC Estandar" selector in
 * ChartOfAccountPage.jsx is unrelated to this importer and is unaffected
 * by it.
 *
 * SIEWIN_GROUP_CATEGORY_2 / SIEWIN_GROUP_CATEGORY_4 below are CLIENT-
 * SPECIFIC, derived and verified against the first real SIEWIN export
 * seen (495 rows, Oct 2026 -- a residential/condominium administration
 * entity, not a cooperative or a commercial company: its own group
 * numbering means something different from both reference templates,
 * e.g. group "13" here is "Cuentas Comerciales por Cobrar", nothing like
 * COMERCIAL's or SOLIDARIO's own group 13). A future SIEWIN file from a
 * DIFFERENT client may use different group numbers/meanings -- that is
 * exactly what unmappedGroups in SIEWIN_IMPORT_INTEGRITY_GAP is for: it
 * fails loudly and lists exactly which group needs a new entry here,
 * rather than guessing from this client's map.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SiewinPucImportService extends TenantAwareService {

    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final CompanyRepository companyRepository;
    private final ChartOfAccountService chartOfAccountService;

    // Same 7 synthesized 1-digit class roots as ChartAccountTemplateImportService
    // -- SIEWIN's own export never has a 1-digit code, so these are always
    // created locally, same names Decreto 2650 uses for all 7 classes this
    // entity model supports.
    private static final Map<Character, String> ROOT_NAMES = Map.of(
            '1', "Activo",
            '2', "Pasivo",
            '3', "Patrimonio",
            '4', "Ingresos",
            '5', "Gastos",
            '6', "Costos de Ventas y de Prestacion de Servicios",
            '7', "Costos de Produccion o de Operacion"
    );

    // Used ONLY for the synthesized roots above -- never a fallback for a
    // real SIEWIN group (see resolveCategory()).
    private static final Map<AccountClass, AccountCategory> CLASS_DEFAULT_CATEGORY = Map.of(
            AccountClass.ASSET, AccountCategory.OTHER_ASSET,
            AccountClass.LIABILITY, AccountCategory.OTHER_LIABILITY,
            AccountClass.EQUITY, AccountCategory.OTHER_EQUITY,
            AccountClass.REVENUE, AccountCategory.OTHER_REVENUE,
            AccountClass.EXPENSE, AccountCategory.OTHER_EXPENSE,
            AccountClass.COST, AccountCategory.COST_OF_SALES
    );

    // 2-digit SIEWIN group (nivel1, re-derived) -> category. Covers every
    // depth-1 group found in the real sample (17 groups) -- used as the
    // fallback when a code's 4-digit prefix isn't in the more specific
    // map below (e.g. the group header itself, or a future client file
    // with a group this client doesn't have children for yet).
    private static final Map<String, AccountCategory> SIEWIN_GROUP_CATEGORY_2 = new HashMap<>();
    static {
        SIEWIN_GROUP_CATEGORY_2.put("11", AccountCategory.CASH_AND_EQUIVALENTS);      // Efectivo y equivalentes al efectivo
        SIEWIN_GROUP_CATEGORY_2.put("12", AccountCategory.INVESTMENT);                // Inversiones e instrumentos financieros
        SIEWIN_GROUP_CATEGORY_2.put("13", AccountCategory.ACCOUNTS_RECEIVABLE);       // Cuentas comerciales por cobrar y otras
        SIEWIN_GROUP_CATEGORY_2.put("15", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Propiedades planta y equipo
        SIEWIN_GROUP_CATEGORY_2.put("22", AccountCategory.ACCOUNTS_PAYABLE);          // Cuentas corrientes comerciales
        SIEWIN_GROUP_CATEGORY_2.put("23", AccountCategory.ACCOUNTS_PAYABLE);          // Obligaciones financieras y cuentas por pagar (default; overridden at 4-digit for tax/payroll subgroups)
        SIEWIN_GROUP_CATEGORY_2.put("25", AccountCategory.ACCRUED_EXPENSES);          // Pasivos por beneficios a los empleados
        SIEWIN_GROUP_CATEGORY_2.put("27", AccountCategory.OTHER_LIABILITY);           // Pasivos por ingresos diferidos
        SIEWIN_GROUP_CATEGORY_2.put("28", AccountCategory.OTHER_LIABILITY);           // Otros pasivos
        SIEWIN_GROUP_CATEGORY_2.put("31", AccountCategory.SHARE_CAPITAL);             // Capital social
        SIEWIN_GROUP_CATEGORY_2.put("33", AccountCategory.RESERVES);                  // Capital social y reservas (its only real child is "Reservas Estatutarias", not share capital -- see class javadoc)
        SIEWIN_GROUP_CATEGORY_2.put("36", AccountCategory.CURRENT_YEAR_PROFIT);       // Resultados del ejercicio
        SIEWIN_GROUP_CATEGORY_2.put("37", AccountCategory.RETAINED_EARNINGS);         // Resultados acumulados
        SIEWIN_GROUP_CATEGORY_2.put("41", AccountCategory.SERVICE_REVENUE);           // Ingresos netos actividad de servicios
        SIEWIN_GROUP_CATEGORY_2.put("42", AccountCategory.FINANCIAL_INCOME);          // Ingresos financieros y otros ingresos (default; overridden at 4-digit for non-financial children)
        SIEWIN_GROUP_CATEGORY_2.put("51", AccountCategory.ADMINISTRATIVE_EXPENSE);    // De administracion
        SIEWIN_GROUP_CATEGORY_2.put("53", AccountCategory.NON_OPERATING_EXPENSE);     // No operacionales
    }

    // 4-digit SIEWIN subgroup (nivel1+nivel2, re-derived) -> category.
    // Covers every depth-2 group found in the real sample (53 groups),
    // checked first by resolveCategory() -- this is the granularity where
    // a group's real meaning actually changes (see class javadoc's "13"/
    // "33"/"23" examples), so it always wins over the 2-digit default
    // above when present.
    private static final Map<String, AccountCategory> SIEWIN_GROUP_CATEGORY_4 = new HashMap<>();
    static {
        SIEWIN_GROUP_CATEGORY_4.put("1105", AccountCategory.CASH_AND_EQUIVALENTS);      // Caja
        SIEWIN_GROUP_CATEGORY_4.put("1110", AccountCategory.CASH_AND_EQUIVALENTS);      // Bancos
        SIEWIN_GROUP_CATEGORY_4.put("1120", AccountCategory.CASH_AND_EQUIVALENTS);      // Cuentas de ahorro
        SIEWIN_GROUP_CATEGORY_4.put("1125", AccountCategory.CASH_AND_EQUIVALENTS);      // Fondos
        SIEWIN_GROUP_CATEGORY_4.put("1210", AccountCategory.INVESTMENT);                // Cuotas o partes de interes social
        SIEWIN_GROUP_CATEGORY_4.put("1225", AccountCategory.INVESTMENT);                // Certificados (CDT)
        SIEWIN_GROUP_CATEGORY_4.put("1328", AccountCategory.ACCOUNTS_RECEIVABLE);       // Aportes por cobrar (cuota de administracion)
        SIEWIN_GROUP_CATEGORY_4.put("1330", AccountCategory.PREPAID_EXPENSES);          // Anticipos y avances a contratistas
        SIEWIN_GROUP_CATEGORY_4.put("1345", AccountCategory.ACCOUNTS_RECEIVABLE);       // Ingresos por cobrar
        SIEWIN_GROUP_CATEGORY_4.put("1365", AccountCategory.ACCOUNTS_RECEIVABLE);       // Cuentas por cobrar trabajadores
        SIEWIN_GROUP_CATEGORY_4.put("1380", AccountCategory.ACCOUNTS_RECEIVABLE);       // Deudores varios
        SIEWIN_GROUP_CATEGORY_4.put("1516", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Construcciones y edificaciones
        SIEWIN_GROUP_CATEGORY_4.put("1520", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Maquinaria y equipo
        SIEWIN_GROUP_CATEGORY_4.put("1524", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Equipo de oficina
        SIEWIN_GROUP_CATEGORY_4.put("1528", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Equipo de computacion y comunicacion
        SIEWIN_GROUP_CATEGORY_4.put("1592", AccountCategory.ACCUMULATED_DEPRECIATION);  // Depreciacion acumulada (contra-asset; see nature override too)
        SIEWIN_GROUP_CATEGORY_4.put("2205", AccountCategory.ACCOUNTS_PAYABLE);          // Nacionales (proveedores)
        SIEWIN_GROUP_CATEGORY_4.put("2335", AccountCategory.ACCOUNTS_PAYABLE);          // Costos y gastos por pagar
        SIEWIN_GROUP_CATEGORY_4.put("2365", AccountCategory.TAXES_PAYABLE);             // Retencion en la fuente
        SIEWIN_GROUP_CATEGORY_4.put("2368", AccountCategory.TAXES_PAYABLE);             // Impuesto de industria y comercio retenido
        SIEWIN_GROUP_CATEGORY_4.put("2370", AccountCategory.ACCRUED_EXPENSES);          // Retenciones y aportes de nomina
        SIEWIN_GROUP_CATEGORY_4.put("2380", AccountCategory.OTHER_LIABILITY);           // Acreedores varios
        SIEWIN_GROUP_CATEGORY_4.put("2505", AccountCategory.ACCRUED_EXPENSES);          // Salarios por pagar
        SIEWIN_GROUP_CATEGORY_4.put("2510", AccountCategory.ACCRUED_EXPENSES);          // Cesantias consolidadas
        SIEWIN_GROUP_CATEGORY_4.put("2515", AccountCategory.ACCRUED_EXPENSES);          // Intereses sobre cesantias
        SIEWIN_GROUP_CATEGORY_4.put("2520", AccountCategory.ACCRUED_EXPENSES);          // Prima de servicios
        SIEWIN_GROUP_CATEGORY_4.put("2525", AccountCategory.ACCRUED_EXPENSES);          // Vacaciones consolidadas
        SIEWIN_GROUP_CATEGORY_4.put("2705", AccountCategory.OTHER_LIABILITY);           // Ingresos recibidos por anticipado
        SIEWIN_GROUP_CATEGORY_4.put("2810", AccountCategory.OTHER_LIABILITY);           // Depositos recibidos
        SIEWIN_GROUP_CATEGORY_4.put("2815", AccountCategory.OTHER_LIABILITY);           // Ingresos recibidos para terceros
        SIEWIN_GROUP_CATEGORY_4.put("3115", AccountCategory.SHARE_CAPITAL);             // Aportes sociales
        SIEWIN_GROUP_CATEGORY_4.put("3310", AccountCategory.RESERVES);                  // Reservas estatutarias
        SIEWIN_GROUP_CATEGORY_4.put("3605", AccountCategory.CURRENT_YEAR_PROFIT);       // Utilidad del ejercicio
        SIEWIN_GROUP_CATEGORY_4.put("3610", AccountCategory.CURRENT_YEAR_PROFIT);       // Perdida del ejercicio
        SIEWIN_GROUP_CATEGORY_4.put("3705", AccountCategory.RETAINED_EARNINGS);         // Utilidades acumuladas
        SIEWIN_GROUP_CATEGORY_4.put("3710", AccountCategory.RETAINED_EARNINGS);         // Perdidas acumuladas
        SIEWIN_GROUP_CATEGORY_4.put("4170", AccountCategory.SERVICE_REVENUE);           // Otras actividades de servicios comunitarios
        SIEWIN_GROUP_CATEGORY_4.put("4175", AccountCategory.SERVICE_REVENUE);           // Devoluciones, rebajas y dctos (DB) -- contra-revenue, same group
        SIEWIN_GROUP_CATEGORY_4.put("4210", AccountCategory.FINANCIAL_INCOME);          // Financieros
        SIEWIN_GROUP_CATEGORY_4.put("4250", AccountCategory.OTHER_REVENUE);             // Recuperaciones
        SIEWIN_GROUP_CATEGORY_4.put("4255", AccountCategory.OTHER_REVENUE);             // Indemnizaciones
        SIEWIN_GROUP_CATEGORY_4.put("4295", AccountCategory.OTHER_REVENUE);             // Diversos
        SIEWIN_GROUP_CATEGORY_4.put("5105", AccountCategory.PERSONNEL_EXPENSE);         // Beneficios a empleados corto plazo
        SIEWIN_GROUP_CATEGORY_4.put("5110", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Honorarios
        SIEWIN_GROUP_CATEGORY_4.put("5130", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Seguros
        SIEWIN_GROUP_CATEGORY_4.put("5135", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Servicios
        SIEWIN_GROUP_CATEGORY_4.put("5140", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Gastos legales
        SIEWIN_GROUP_CATEGORY_4.put("5145", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Mantenimiento y reparaciones
        SIEWIN_GROUP_CATEGORY_4.put("5150", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Adecuacion e instalacion
        SIEWIN_GROUP_CATEGORY_4.put("5160", AccountCategory.DEPRECIATION_EXPENSE);      // Depreciaciones, amortizaciones y deterioro
        SIEWIN_GROUP_CATEGORY_4.put("5195", AccountCategory.OTHER_EXPENSE);             // Diversos
        SIEWIN_GROUP_CATEGORY_4.put("5305", AccountCategory.FINANCIAL_EXPENSE);         // Gastos financieros
        SIEWIN_GROUP_CATEGORY_4.put("5315", AccountCategory.NON_OPERATING_EXPENSE);     // Otros gastos
    }

    // Contra-asset "depreciacion acumulada" group for this client -- same
    // prefix-based mechanism as ChartAccountTemplateImportService (its
    // children, e.g. "159205 Construcciones y edificaciones", don't repeat
    // "acumulada" in their own name, so this has to be a prefix match, not
    // a keyword match).
    private static final List<String> SIEWIN_ACCUMULATED_DEPRECIATION_PREFIXES = List.of("1592");

    public SiewinPucImportResultDTO importFromSiewin(SiewinPucImportRequest request) {
        Long companyId = currentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new InvalidOperationException(
                        "Company not found for the current session.", "COMPANY_NOT_FOUND"));

        // Decision 5 (2026-10-31, approved): same precondition as Fase 4 --
        // no merge-into-a-non-empty-plan mode exists.
        if (chartOfAccountsRepository.existsByCompanyId(companyId)) {
            throw new InvalidOperationException(
                    "El Plan de Cuentas ya tiene cuentas registradas. " +
                            "La importacion desde SIEWIN solo esta disponible con el plan vacio.",
                    "CHART_OF_ACCOUNTS_NOT_EMPTY"
            );
        }

        List<SiewinPucRowDTO> rows = request == null ? null : request.rows();
        if (rows == null || rows.isEmpty()) {
            throw new InvalidOperationException(
                    "El archivo no contiene filas para importar.", "SIEWIN_EMPTY_FILE");
        }

        // ── Pass 1: per-row checks + code re-derivation ──────────────────
        List<DerivedRow> derivedRows = new ArrayList<>();
        List<String> outOfRangeLevels = new ArrayList<>();
        List<String> levelSkipGaps = new ArrayList<>();
        List<String> missingNames = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            SiewinPucRowDTO row = rows.get(i);
            String label = rowLabel(row, i);

            if (row.nombre() == null || row.nombre().isBlank()) {
                missingNames.add(label);
            }

            int[] levels = {
                    nz(row.nivel1()), nz(row.nivel2()), nz(row.nivel3()),
                    nz(row.nivel4()), nz(row.nivel5()), nz(row.nivel6())
            };

            int depth = 1;
            boolean seenZero = false;
            boolean gap = false;
            for (int lvl = 1; lvl < levels.length; lvl++) {
                if (levels[lvl] != 0) {
                    if (seenZero) {
                        gap = true;
                    }
                    depth++;
                } else {
                    seenZero = true;
                }
            }
            if (gap) {
                // A deeper level is populated while a shallower one is 0 --
                // the code can't be safely reconstructed. Report and skip
                // (decision 2's spirit: never guess).
                levelSkipGaps.add(label);
                continue;
            }

            StringBuilder codeBuilder = new StringBuilder();
            boolean rowOutOfRange = false;
            for (int lvl = 0; lvl < depth; lvl++) {
                int value = levels[lvl];
                if (value > 99) {
                    outOfRangeLevels.add(label + " (nivel" + (lvl + 1) + "=" + value + ")");
                    rowOutOfRange = true;
                    break;
                }
                codeBuilder.append(String.format("%02d", value));
            }
            if (rowOutOfRange) {
                continue;
            }

            derivedRows.add(new DerivedRow(codeBuilder.toString(), row));
        }

        // ── Pass 2: duplicates, orphans, posting-account-has-children ───
        Map<String, DerivedRow> byCode = new LinkedHashMap<>();
        List<String> duplicateCodes = new ArrayList<>();
        for (DerivedRow d : derivedRows) {
            if (byCode.containsKey(d.code())) {
                duplicateCodes.add(d.code() + " - " + d.row().nombre());
            } else {
                byCode.put(d.code(), d);
            }
        }

        List<String> orphanCodes = new ArrayList<>();
        for (String code : byCode.keySet()) {
            String parentCode = parentCodeOf(code);
            if (parentCode != null && parentCode.length() >= 2 && !byCode.containsKey(parentCode)) {
                orphanCodes.add(code + " (esperaba el grupo '" + parentCode + "')");
            }
        }

        Set<String> referencedAsParent = new HashSet<>();
        for (String code : byCode.keySet()) {
            String parentCode = parentCodeOf(code);
            if (parentCode != null) {
                referencedAsParent.add(parentCode);
            }
        }

        List<String> postingAccountHasChildren = new ArrayList<>();
        for (DerivedRow d : byCode.values()) {
            if (nz(d.row().imputable()) == 1 && referencedAsParent.contains(d.code())) {
                postingAccountHasChildren.add(d.code() + " - " + d.row().nombre());
            }
        }

        // ── Pass 3: category coverage (collected upfront, not thrown per-row) ──
        List<String> unmappedGroups = new ArrayList<>();
        for (DerivedRow d : byCode.values()) {
            if (lookupCategory(d.code()) == null) {
                unmappedGroups.add(d.code() + " - " + d.row().nombre());
            }
        }

        if (!outOfRangeLevels.isEmpty() || !levelSkipGaps.isEmpty() || !duplicateCodes.isEmpty()
                || !orphanCodes.isEmpty() || !postingAccountHasChildren.isEmpty()
                || !missingNames.isEmpty() || !unmappedGroups.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("outOfRangeLevels", outOfRangeLevels);
            details.put("levelSkipGaps", levelSkipGaps);
            details.put("duplicateCodes", duplicateCodes);
            details.put("orphanCodes", orphanCodes);
            details.put("postingAccountHasChildren", postingAccountHasChildren);
            details.put("missingNames", missingNames);
            details.put("unmappedGroups", unmappedGroups);

            log.error("IMPORT_SIEWIN_INTEGRITY_GAP | tenant: {} | details: {}", companyId, details);
            throw new InvalidOperationException(
                    "El archivo SIEWIN tiene problemas que impiden la importacion. Revise los detalles.",
                    "SIEWIN_IMPORT_INTEGRITY_GAP", details);
        }

        // ── Everything validated -- create roots, then every real row ───
        SortedSet<Character> rootDigitsNeeded = new TreeSet<>();
        for (String code : byCode.keySet()) {
            rootDigitsNeeded.add(code.charAt(0));
        }

        Map<String, Long> codeToId = new HashMap<>();
        int created = 0;
        for (char digit : rootDigitsNeeded) {
            String rootCode = String.valueOf(digit);
            AccountClass accountClass = AccountClass.fromCode(rootCode);
            Long id = createOne(rootCode, ROOT_NAMES.get(digit), accountClass,
                    CLASS_DEFAULT_CATEGORY.get(accountClass),
                    AccountNature.fromAccountClass(accountClass), false, null, false, false);
            codeToId.put(rootCode, id);
            created++;
        }
        int rootAccountsCreated = created;

        List<String> orderedCodes = new ArrayList<>(byCode.keySet());
        orderedCodes.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

        List<String> truncatedNameWarnings = new ArrayList<>();
        for (String code : orderedCodes) {
            DerivedRow d = byCode.get(code);
            String parentCode = parentCodeOf(code);
            Long parentId = codeToId.get(parentCode);
            if (parentId == null) {
                // Pass 2's orphan check above already guarantees every
                // parent exists -- reaching here would be a bug in that
                // check, not a data gap. Fail loudly either way.
                throw new InvalidOperationException(String.format(
                        "No se pudo resolver el padre '%s' para el codigo '%s' durante la importacion.",
                        parentCode, code), "IMPORT_PUC_UNRESOLVED_PARENT");
            }

            String name = d.row().nombre().trim();
            AccountClass accountClass = AccountClass.fromCode(code);
            AccountCategory category = resolveCategory(code, name);
            AccountNature nature = resolveNature(code, name, accountClass);
            boolean postingAccount = nz(d.row().imputable()) == 1;
            boolean requiresThirdParty = nz(d.row().bare()) == 6;
            boolean requiresCostCenter = nz(d.row().ceco()) != 0;

            if (looksTruncated(name)) {
                truncatedNameWarnings.add(code + " - " + name);
            }

            Long id = createOne(code, name, accountClass, category, nature,
                    postingAccount, parentId, requiresThirdParty, requiresCostCenter);
            codeToId.put(code, id);
            created++;
        }

        log.info("IMPORT_SIEWIN_DONE | tenant: {} | accountsCreated: {} | rootAccountsCreated: {} | truncatedNameWarnings: {}",
                companyId, created, rootAccountsCreated, truncatedNameWarnings.size());

        return new SiewinPucImportResultDTO(created, rootAccountsCreated, truncatedNameWarnings);
    }

    /**
     * code's own 4-digit prefix in SIEWIN_GROUP_CATEGORY_4 first (the
     * granularity where a group's real meaning actually changes -- see
     * class javadoc), falling back to the 2-digit prefix. Returns null
     * when neither map has an entry -- callers decide whether that's a
     * validation failure (unmappedGroups, collected upfront) or an actual
     * throw (resolveCategory(), only reached after validation passed).
     */
    private AccountCategory lookupCategory(String code) {
        if (code.length() >= 4) {
            AccountCategory mapped = SIEWIN_GROUP_CATEGORY_4.get(code.substring(0, 4));
            if (mapped != null) {
                return mapped;
            }
        }
        if (code.length() >= 2) {
            return SIEWIN_GROUP_CATEGORY_2.get(code.substring(0, 2));
        }
        return null;
    }

    private AccountCategory resolveCategory(String code, String name) {
        AccountCategory mapped = lookupCategory(code);
        if (mapped != null) {
            return mapped;
        }
        // Unreachable in practice -- the unmappedGroups check above
        // already blocks the import before this method is ever called
        // for a code it can't resolve. Kept as a defensive throw, not a
        // silent default, per the fail-fast philosophy.
        throw new InvalidOperationException(String.format(
                "El grupo de la cuenta '%s - %s' no tiene una categoria asignada.", code, name),
                "SIEWIN_UNMAPPED_GROUP");
    }

    /**
     * Same priority order as ChartAccountTemplateImportService.resolveNature()
     * (see that class's javadoc), adapted to this client's own accumulated-
     * depreciation group prefix list.
     */
    private AccountNature resolveNature(String code, String name, AccountClass accountClass) {
        String upperName = name.toUpperCase();

        if (upperName.contains("(DB)")) {
            return AccountNature.D;
        }
        if (upperName.contains("(CR)")) {
            return AccountNature.C;
        }

        if (accountClass == AccountClass.ASSET) {
            if (upperName.contains("PROVISION")) {
                return AccountNature.C;
            }
            if (upperName.contains("DEPRECIACION ACUMULADA")
                    || upperName.contains("AMORTIZACION ACUMULADA")
                    || upperName.contains("AGOTAMIENTO ACUMULADO")) {
                return AccountNature.C;
            }
            for (String prefix : SIEWIN_ACCUMULATED_DEPRECIATION_PREFIXES) {
                if (code.startsWith(prefix)) {
                    return AccountNature.C;
                }
            }
        }

        if (accountClass == AccountClass.REVENUE && upperName.contains("DEVOLUCION")) {
            return AccountNature.D;
        }

        if ((accountClass == AccountClass.COST || accountClass == AccountClass.EXPENSE)
                && upperName.contains("DEVOLUCION")) {
            return AccountNature.C;
        }

        return AccountNature.fromAccountClass(accountClass);
    }

    /**
     * Non-blocking heuristic (section 4 of the proposal doc): at least 10
     * names in the real sample end mid-word right around 40-41 characters,
     * grounded in the source DDL's own mention of a Java jTextField widget
     * -- almost certainly a UI input-length limit in SIEWIN itself, not
     * something this importer can repair (the full word isn't recoverable
     * from the data). Flags that narrow length band only, never every long
     * name, so this stays a targeted warning, not noise.
     */
    private boolean looksTruncated(String name) {
        if (name == null) {
            return false;
        }
        int length = name.trim().length();
        return length >= 40 && length <= 41;
    }

    private String rowLabel(SiewinPucRowDTO row, int index) {
        String name = row.nombre() == null ? "" : row.nombre().trim();
        return "fila " + (index + 1) + (name.isEmpty() ? "" : " (" + name + ")");
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    /** Null for a 1-digit root; first char for a 2-digit code; otherwise length-2 -- same rule as ChartAccountTemplateImportService. */
    private String parentCodeOf(String code) {
        if (code.length() <= 1) {
            return null;
        }
        if (code.length() == 2) {
            return code.substring(0, 1);
        }
        return code.substring(0, code.length() - 2);
    }

    private Long createOne(String code, String name, AccountClass accountClass, AccountCategory category,
                            AccountNature nature, boolean postingAccount, Long parentId,
                            boolean requiresThirdParty, boolean requiresCostCenter) {
        ChartOfAccountRequest request = new ChartOfAccountRequest();
        request.setCode(code);
        request.setName(truncateName(name));
        request.setAccountClass(accountClass);
        request.setAccountCategory(category);
        request.setNature(nature);
        request.setFinancialStatement(FinancialStatement.fromAccountClass(accountClass));
        request.setPostingAccount(postingAccount);
        request.setRequiresThirdParty(requiresThirdParty);
        request.setRequiresCostCenter(requiresCostCenter);
        request.setActive(true);
        request.setParentId(parentId);

        return chartOfAccountService.create(request).getId();
    }

    // ChartOfAccounts.name is VARCHAR(150) -- SIEWIN names never get close,
    // but this keeps things safe if that ever changes.
    private String truncateName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 150 ? name.substring(0, 150) : name;
    }

    private record DerivedRow(String code, SiewinPucRowDTO row) {
    }
}
