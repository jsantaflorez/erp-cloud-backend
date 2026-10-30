package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountRequest;
import com.erp.erp_cloud.dto.ChartTemplateImportResultDTO;
import com.erp.erp_cloud.dto.ImportChartTemplateRequest;
import com.erp.erp_cloud.entity.ChartAccountTemplateEntry;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountCategory;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.enums.AccountNature;
import com.erp.erp_cloud.enums.ChartTemplateType;
import com.erp.erp_cloud.enums.FinancialStatement;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.ChartAccountTemplateRepository;
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
 * Fase 4 (Multiempresa) -- "Importar PUC Estandar": clones the reference
 * ChartAccountTemplateEntry rows (today used only for name suggestions,
 * see ChartAccountTemplateService) into REAL ChartOfAccounts rows for the
 * current tenant, going through ChartOfAccountService.create() for every
 * single node so every structural rule it already enforces (digit-jump
 * validation, parent/company checks, category-class matching, duplicate
 * codes...) applies here exactly as it would for a manually-typed account.
 *
 * ChartAccountTemplateEntry only carries code+name -- no class, category,
 * nature, financial statement, hierarchy or posting-account flag. All of
 * that is DERIVED here, from the Colombian PUC code convention and the
 * fixed, template-specific tables below.
 *
 * ---------------------------------------------------------------------
 * Four binding rules behind this version of the importer (2026-10-30,
 * per explicit user direction after a review of the first version):
 *
 * 1. COMERCIAL (Decreto 2650 / NIIF Pymes) is the primary, high-volume
 *    standard and stays rigorously validated: COMERCIAL_GROUP_CATEGORY
 *    below has a specific entry for every one of its 40 real groups (the
 *    39 seeded today plus '47', added by the 2026-10-30 SQL fix once the
 *    missing-row defect below is applied) -- CLASS_DEFAULT_CATEGORY is
 *    NEVER used as a fallback for a real COMERCIAL group, only for the
 *    7 synthesized 1-digit class roots.
 *
 * 2. SOLIDARIO gets the same rigor, not a generic shortcut. Its groups
 *    mean something different from COMERCIAL's (e.g. group 14 is
 *    "Cartera de creditos" -- the loan portfolio to members/associates,
 *    not inventory, and nothing like COMERCIAL's own group 14) and
 *    SOLIDARIO_GROUP_CATEGORY below maps all 39 of its real groups
 *    individually, verified against the actual group names in
 *    sql/2026-09-10_chart_account_templates.sql. Group 14 resolves to
 *    ACCOUNTS_RECEIVABLE (the closest real category to a credit
 *    portfolio), never to OTHER_ASSET.
 *
 * 3. Both templates are kept fully separate, explicit maps -- there is
 *    no "guess from the code" or cross-template fallback. Every mapping
 *    decision here was taken by reading each group's/account's own name
 *    in the seed data, not inferred from its numeric position.
 *
 * 4. Zero "huecos": validateIntegrity() below collects EVERY code whose
 *    expected parent group is missing from the template, and EVERY code
 *    whose length isn't a valid PUC digit-jump, and throws a single
 *    descriptive exception listing all of them if any exist, rather than
 *    silently skipping or synthesizing a placeholder parent. The 3 real
 *    seed-data defects found while building this (COMERCIAL missing
 *    group '47' as its own row; SOLIDARIO '734730'/'734735' duplicate/
 *    corrupted rows of '736005'/'736010'; SOLIDARIO '251002113' a 9-digit
 *    typo of '25100213') are fixed at the SOURCE, in
 *    sql/2026-10-30_fix_chart_account_templates_integrity.sql, which the
 *    user must run before this importer is used -- this validation stays
 *    afterwards as a permanent defensive gate against future seed-data
 *    regressions, not as a repair mechanism.
 * ---------------------------------------------------------------------
 *
 * Deriving each field:
 *  - accountClass: AccountClass.fromCode() (first digit) -- unchanged,
 *    already the single source of truth used everywhere else.
 *  - accountCategory: resolveCategory() below -- the specific group map
 *    for the account's own template, with a small, verified prefix
 *    override for the accumulated-depreciation/amortization/depletion
 *    contra-asset groups in each template (see
 *    *_ACCUMULATED_DEPRECIATION_PREFIXES). Any real (non-root) group
 *    code that isn't in either map is a template change this importer
 *    doesn't know about yet -- it fails loudly (TEMPLATE_UNMAPPED_GROUP)
 *    instead of silently defaulting, per rule 4 above.
 *  - nature: resolveNature() below -- a rule-based resolver, not a
 *    blanket class default. Priority: an explicit "(DB)"/"(CR)" marker
 *    literally present in the template row's own name (used by the PUC
 *    seed data itself for ~50 rows, e.g. "Capital por suscribir (DB)",
 *    "Inversiones (CR)") always wins; then, for ASSET-class rows only,
 *    the "PROVISION"/"Provisiones" keyword (contra-asset loss
 *    provisions, Credit) and the verified accumulated-depreciation
 *    group prefixes (Credit); then, for REVENUE-class rows, the
 *    "DEVOLUCION" keyword (sales returns, contra-revenue, Debit); then,
 *    for COST/EXPENSE-class rows, the same "DEVOLUCION" keyword
 *    (purchase returns, contra-cost/expense, Credit); otherwise the
 *    existing class-level default (AccountNature.fromAccountClass()).
 *    Nature is informational only in this codebase (reports orient by
 *    SECTION polarity, never by a single account's own nature field --
 *    see AccountNature javadoc), so this resolver only improves display
 *    correctness, it never changes a report total.
 *  - financialStatement: unchanged, the existing fromAccountClass()
 *    helper.
 *  - postingAccount: computed from the template data itself -- true
 *    only for a code that has no deeper code in the same template using
 *    it as a prefix at the next valid digit-jump. Correct for both
 *    templates even though COMERCIAL tops out at 6 digits while
 *    SOLIDARIO goes to 8.
 *
 * One kind of code is deliberately excluded, not a gap: "cuentas de
 * orden" (first digit 8 or 9, contingent/memo accounts) have no
 * AccountClass at all -- the entity model here was never built to carry
 * off-balance-sheet memo accounts, so these rows are skipped (counted,
 * never silently dropped without being reported in the result DTO).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChartAccountTemplateImportService extends TenantAwareService {

    private final ChartAccountTemplateRepository templateRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final CompanyRepository companyRepository;
    private final ChartOfAccountService chartOfAccountService;

    // ═══════════════════════════════════════════════════════════
    // Root class accounts (1 digit) -- never seeded in the template
    // (ChartAccountTemplateEntry only ever starts at 2 digits), so they
    // are synthesized here, same names Decreto 2650 uses for all 7
    // classes this entity model supports (8/9, cuentas de orden, are
    // excluded -- see class javadoc).
    // ═══════════════════════════════════════════════════════════
    private static final Map<Character, String> ROOT_NAMES = Map.of(
            '1', "Activo",
            '2', "Pasivo",
            '3', "Patrimonio",
            '4', "Ingresos",
            '5', "Gastos",
            '6', "Costos de Ventas y de Prestacion de Servicios",
            '7', "Costos de Produccion o de Operacion"
    );

    // Used ONLY for the 7 synthesized roots above. Never used as a
    // fallback for a real group in either template -- see rules 1/2/4.
    private static final Map<AccountClass, AccountCategory> CLASS_DEFAULT_CATEGORY = Map.of(
            AccountClass.ASSET, AccountCategory.OTHER_ASSET,
            AccountClass.LIABILITY, AccountCategory.OTHER_LIABILITY,
            AccountClass.EQUITY, AccountCategory.OTHER_EQUITY,
            AccountClass.REVENUE, AccountCategory.OTHER_REVENUE,
            AccountClass.EXPENSE, AccountCategory.OTHER_EXPENSE,
            AccountClass.COST, AccountCategory.COST_OF_SALES
    );

    // Group (2-digit code) -> category for PUC COMERCIAL (Decreto 2650 /
    // NIIF Pymes), built and verified against every real group name in
    // sql/2026-09-10_chart_account_templates.sql. Covers all 40 real
    // groups (39 seeded today + '47', added by the 2026-10-30 SQL fix) --
    // see rule 1 above.
    private static final Map<String, AccountCategory> COMERCIAL_GROUP_CATEGORY = new HashMap<>();
    static {
        COMERCIAL_GROUP_CATEGORY.put("11", AccountCategory.CASH_AND_EQUIVALENTS);      // Disponible
        COMERCIAL_GROUP_CATEGORY.put("12", AccountCategory.INVESTMENT);                // Inversiones
        COMERCIAL_GROUP_CATEGORY.put("13", AccountCategory.ACCOUNTS_RECEIVABLE);       // Deudores
        COMERCIAL_GROUP_CATEGORY.put("14", AccountCategory.INVENTORY);                 // Inventarios
        COMERCIAL_GROUP_CATEGORY.put("15", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Propiedades, planta y equipo
        COMERCIAL_GROUP_CATEGORY.put("16", AccountCategory.INTANGIBLE_ASSET);          // Intangibles
        COMERCIAL_GROUP_CATEGORY.put("17", AccountCategory.PREPAID_EXPENSES);          // Diferidos (activo)
        COMERCIAL_GROUP_CATEGORY.put("18", AccountCategory.OTHER_ASSET);               // Otros activos
        COMERCIAL_GROUP_CATEGORY.put("19", AccountCategory.OTHER_ASSET);               // Valorizaciones
        COMERCIAL_GROUP_CATEGORY.put("21", AccountCategory.SHORT_TERM_DEBT);           // Obligaciones financieras
        COMERCIAL_GROUP_CATEGORY.put("22", AccountCategory.ACCOUNTS_PAYABLE);          // Proveedores
        COMERCIAL_GROUP_CATEGORY.put("23", AccountCategory.ACCOUNTS_PAYABLE);          // Cuentas por pagar
        COMERCIAL_GROUP_CATEGORY.put("24", AccountCategory.TAXES_PAYABLE);             // Impuestos, gravamenes y tasas
        COMERCIAL_GROUP_CATEGORY.put("25", AccountCategory.ACCRUED_EXPENSES);          // Obligaciones laborales
        COMERCIAL_GROUP_CATEGORY.put("26", AccountCategory.ACCRUED_EXPENSES);          // Pasivos estimados y provisiones
        COMERCIAL_GROUP_CATEGORY.put("27", AccountCategory.OTHER_LIABILITY);           // Diferidos (pasivo)
        COMERCIAL_GROUP_CATEGORY.put("28", AccountCategory.OTHER_LIABILITY);           // Otros pasivos
        COMERCIAL_GROUP_CATEGORY.put("29", AccountCategory.LONG_TERM_DEBT);            // Bonos y papeles comerciales
        COMERCIAL_GROUP_CATEGORY.put("31", AccountCategory.SHARE_CAPITAL);             // Capital social
        COMERCIAL_GROUP_CATEGORY.put("32", AccountCategory.ADDITIONAL_PAID_IN_CAPITAL);// Superavit de capital
        COMERCIAL_GROUP_CATEGORY.put("33", AccountCategory.RESERVES);                  // Reservas
        COMERCIAL_GROUP_CATEGORY.put("34", AccountCategory.OTHER_EQUITY);              // Revalorizacion del patrimonio
        COMERCIAL_GROUP_CATEGORY.put("35", AccountCategory.OTHER_EQUITY);              // Dividendos/participaciones decretados en acciones
        COMERCIAL_GROUP_CATEGORY.put("36", AccountCategory.CURRENT_YEAR_PROFIT);       // Resultados del ejercicio
        COMERCIAL_GROUP_CATEGORY.put("37", AccountCategory.RETAINED_EARNINGS);         // Resultados de ejercicios anteriores
        COMERCIAL_GROUP_CATEGORY.put("38", AccountCategory.OTHER_EQUITY);              // Superavit por valorizaciones
        COMERCIAL_GROUP_CATEGORY.put("41", AccountCategory.OPERATING_REVENUE);         // Operacionales (ingresos)
        COMERCIAL_GROUP_CATEGORY.put("42", AccountCategory.NON_OPERATING_REVENUE);     // No operacionales (ingresos)
        COMERCIAL_GROUP_CATEGORY.put("47", AccountCategory.NON_OPERATING_REVENUE);     // Ajustes por inflacion (ingresos)
        COMERCIAL_GROUP_CATEGORY.put("51", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Operacionales de administracion
        COMERCIAL_GROUP_CATEGORY.put("52", AccountCategory.SALES_EXPENSE);             // Operacionales de ventas
        COMERCIAL_GROUP_CATEGORY.put("53", AccountCategory.NON_OPERATING_EXPENSE);     // No operacionales (gastos)
        COMERCIAL_GROUP_CATEGORY.put("54", AccountCategory.TAX_EXPENSE);               // Impuesto de renta y complementarios
        COMERCIAL_GROUP_CATEGORY.put("59", AccountCategory.OTHER_EXPENSE);             // Ganancias y perdidas
        COMERCIAL_GROUP_CATEGORY.put("61", AccountCategory.COST_OF_SALES);             // Costo de ventas y de prestacion de servicios
        COMERCIAL_GROUP_CATEGORY.put("62", AccountCategory.COST_OF_GOODS_SOLD);        // Compras
        COMERCIAL_GROUP_CATEGORY.put("71", AccountCategory.COST_OF_GOODS_SOLD);        // Materia prima
        COMERCIAL_GROUP_CATEGORY.put("72", AccountCategory.COST_OF_GOODS_SOLD);        // Mano de obra directa
        COMERCIAL_GROUP_CATEGORY.put("73", AccountCategory.COST_OF_GOODS_SOLD);        // Costos indirectos
        COMERCIAL_GROUP_CATEGORY.put("74", AccountCategory.COST_OF_SERVICES);          // Contratos de servicios
    }

    // Group (2-digit code) -> category for PUC SOLIDARIO (sector
    // cooperativo/fondos de empleados), built and verified against every
    // real group name in sql/2026-09-10_chart_account_templates.sql.
    // Covers all 39 real SOLIDARIO groups individually -- see rule 2
    // above. Group '14' (CARTERA DE CREDITOS, the loan portfolio to
    // members) resolves to ACCOUNTS_RECEIVABLE, not to OTHER_ASSET.
    private static final Map<String, AccountCategory> SOLIDARIO_GROUP_CATEGORY = new HashMap<>();
    static {
        SOLIDARIO_GROUP_CATEGORY.put("11", AccountCategory.CASH_AND_EQUIVALENTS);      // Disponible
        SOLIDARIO_GROUP_CATEGORY.put("12", AccountCategory.INVESTMENT);                // Inversiones
        SOLIDARIO_GROUP_CATEGORY.put("13", AccountCategory.INVENTORY);                 // Inventarios
        SOLIDARIO_GROUP_CATEGORY.put("14", AccountCategory.ACCOUNTS_RECEIVABLE);       // Cartera de creditos
        SOLIDARIO_GROUP_CATEGORY.put("16", AccountCategory.ACCOUNTS_RECEIVABLE);       // Cuentas por cobrar
        SOLIDARIO_GROUP_CATEGORY.put("17", AccountCategory.PROPERTY_PLANT_EQUIPMENT);  // Propiedades, planta y equipo
        SOLIDARIO_GROUP_CATEGORY.put("18", AccountCategory.PREPAID_EXPENSES);          // Diferido (activo)
        SOLIDARIO_GROUP_CATEGORY.put("19", AccountCategory.OTHER_ASSET);               // Otros activos
        SOLIDARIO_GROUP_CATEGORY.put("21", AccountCategory.CURRENT_LIABILITY);         // Depositos (de asociados)
        SOLIDARIO_GROUP_CATEGORY.put("22", AccountCategory.SHORT_TERM_DEBT);           // Pactos de recompra
        SOLIDARIO_GROUP_CATEGORY.put("23", AccountCategory.SHORT_TERM_DEBT);           // Creditos de bancos y otras obligaciones financieras
        SOLIDARIO_GROUP_CATEGORY.put("24", AccountCategory.ACCOUNTS_PAYABLE);          // Cuentas por pagar
        SOLIDARIO_GROUP_CATEGORY.put("25", AccountCategory.TAXES_PAYABLE);             // Impuestos, gravamenes
        SOLIDARIO_GROUP_CATEGORY.put("26", AccountCategory.OTHER_LIABILITY);           // Fondos sociales, mutuales y otros
        SOLIDARIO_GROUP_CATEGORY.put("27", AccountCategory.OTHER_LIABILITY);           // Otros pasivos
        SOLIDARIO_GROUP_CATEGORY.put("28", AccountCategory.ACCRUED_EXPENSES);          // Pasivos estimados y provisiones
        SOLIDARIO_GROUP_CATEGORY.put("29", AccountCategory.LONG_TERM_DEBT);            // Titulos de inversion en circulacion
        SOLIDARIO_GROUP_CATEGORY.put("31", AccountCategory.SHARE_CAPITAL);             // Capital social (aportes)
        SOLIDARIO_GROUP_CATEGORY.put("32", AccountCategory.RESERVES);                  // Reservas
        SOLIDARIO_GROUP_CATEGORY.put("33", AccountCategory.RESERVES);                  // Fondos de destinacion especifica
        SOLIDARIO_GROUP_CATEGORY.put("34", AccountCategory.OTHER_EQUITY);              // Superavit (donaciones/valorizaciones)
        SOLIDARIO_GROUP_CATEGORY.put("35", AccountCategory.CURRENT_YEAR_PROFIT);       // Resultados del ejercicio
        SOLIDARIO_GROUP_CATEGORY.put("36", AccountCategory.RETAINED_EARNINGS);         // Resultados de ejercicios anteriores
        SOLIDARIO_GROUP_CATEGORY.put("41", AccountCategory.OPERATING_REVENUE);         // Operacionales (ingresos)
        SOLIDARIO_GROUP_CATEGORY.put("42", AccountCategory.NON_OPERATING_REVENUE);     // No operacionales (ingresos)
        SOLIDARIO_GROUP_CATEGORY.put("43", AccountCategory.OTHER_REVENUE);             // Ingresos de ejercicios anteriores
        SOLIDARIO_GROUP_CATEGORY.put("47", AccountCategory.NON_OPERATING_REVENUE);     // Ajustes por inflacion (ingresos)
        SOLIDARIO_GROUP_CATEGORY.put("51", AccountCategory.ADMINISTRATIVE_EXPENSE);    // Gastos de administracion
        SOLIDARIO_GROUP_CATEGORY.put("52", AccountCategory.SALES_EXPENSE);             // Operacionales de ventas
        SOLIDARIO_GROUP_CATEGORY.put("53", AccountCategory.NON_OPERATING_EXPENSE);     // No operacionales (gastos)
        SOLIDARIO_GROUP_CATEGORY.put("54", AccountCategory.OTHER_EXPENSE);             // Gastos ejercicios anteriores
        SOLIDARIO_GROUP_CATEGORY.put("58", AccountCategory.TAX_EXPENSE);               // Impuesto de renta y complementarios
        SOLIDARIO_GROUP_CATEGORY.put("59", AccountCategory.OTHER_EXPENSE);             // Excedentes y/o perdidas
        SOLIDARIO_GROUP_CATEGORY.put("61", AccountCategory.COST_OF_SALES);             // Costo de ventas y de prestacion de servicios
        SOLIDARIO_GROUP_CATEGORY.put("62", AccountCategory.COST_OF_GOODS_SOLD);        // Compras
        SOLIDARIO_GROUP_CATEGORY.put("71", AccountCategory.COST_OF_GOODS_SOLD);        // Materia prima
        SOLIDARIO_GROUP_CATEGORY.put("72", AccountCategory.COST_OF_GOODS_SOLD);        // Mano de obra directa
        SOLIDARIO_GROUP_CATEGORY.put("73", AccountCategory.COST_OF_GOODS_SOLD);        // Costos indirectos
        SOLIDARIO_GROUP_CATEGORY.put("74", AccountCategory.COST_OF_SERVICES);          // Contratos de servicios
    }

    // Contra-asset "accumulated depreciation/amortization/depletion"
    // groups -- get ACCUMULATED_DEPRECIATION category and Credit nature
    // regardless of their parent group's own category/class default.
    // Verified by inspecting every ASSET-class 4-digit group name in
    // both templates: these are the only ones whose OWN name is an
    // accumulated-depreciation/amortization/depletion header (their
    // 6-digit children name the specific asset, e.g. "Maquinaria y
    // equipo", not the word "acumulada" again, so this has to be
    // prefix-based, not a keyword match on each row's own name).
    // '1596' ("Depreciacion diferida") and '1599' ("Provisiones") are
    // deliberately NOT in this list -- the first is a distinct, normal
    // Debit-nature asset account (deferred fiscal/accounting depreciation
    // difference, not a contra account), and the second is covered by
    // the ASSET+"PROVISION" nature rule below while keeping its parent
    // group's own category.
    private static final List<String> COMERCIAL_ACCUMULATED_DEPRECIATION_PREFIXES =
            List.of("1592", "1597", "1598", "1698", "1798");
    private static final List<String> SOLIDARIO_ACCUMULATED_DEPRECIATION_PREFIXES =
            List.of("1795");

    public ChartTemplateImportResultDTO importTemplate(ImportChartTemplateRequest request) {
        Long companyId = currentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new InvalidOperationException(
                        "Company not found for the current session.", "COMPANY_NOT_FOUND"));

        // Rule (2026-10-30, explicit user direction): the only thing that
        // locks a company to a template is having an actual account --
        // never the mere fact that Company.chartTemplate already holds a
        // value. Companies are provisioned directly in the database today
        // (see CompanyController), so a brand-new company can easily end
        // up with chartTemplate pre-set (a typo'd NIT at creation, a copy-
        // pasted seed row) despite having zero ChartOfAccounts rows -- it
        // must stay free to pick either template until it actually has
        // something to protect. This check runs BEFORE resolving which
        // template to use, and is unconditional: it fires regardless of
        // what the request asks for, so there is no path left where an
        // account already exists and a template mismatch could still slip
        // through -- CHART_TEMPLATE_MISMATCH (the old guard that compared
        // the request against Company.chartTemplate) is gone, this single
        // check now does that job correctly, anchored to real data instead
        // of a field that can be set with nothing behind it.
        if (chartOfAccountsRepository.existsByCompanyId(companyId)) {
            throw new InvalidOperationException(
                    "El Plan de Cuentas ya tiene cuentas registradas. " +
                            "La importacion del PUC estandar solo esta disponible con el plan vacio.",
                    "CHART_OF_ACCOUNTS_NOT_EMPTY"
            );
        }

        ChartTemplateType effectiveType = resolveTemplateType(company, request);

        // Plan is confirmed empty above -- always (re)persist the chosen
        // template, even if it overwrites a pre-set value, so the company
        // record reflects what was actually imported.
        company.setChartTemplate(effectiveType);
        companyRepository.save(company);

        List<ChartAccountTemplateEntry> entries =
                templateRepository.findByTemplateTypeOrderByCode(effectiveType);

        // code -> name, filtering out only the deliberately out-of-scope
        // cuentas de orden (first digit 8/9 -- see class javadoc). Every
        // other row is kept as-is: malformed codes and hierarchy gaps are
        // NOT filtered here, they are collected and reported explicitly
        // by validateIntegrity() below.
        Map<String, String> codeToName = new LinkedHashMap<>();
        int memoSkipped = 0;
        for (ChartAccountTemplateEntry entry : entries) {
            String code = entry.getCode();
            char first = code.charAt(0);
            if (first == '8' || first == '9') {
                memoSkipped++;
                continue;
            }
            codeToName.putIfAbsent(code, entry.getName());
        }

        validateIntegrity(effectiveType, codeToName);

        // Which codes are referenced as someone else's parent -- decides
        // postingAccount (leaf = never referenced as a parent).
        Set<String> referencedAsParent = new HashSet<>();
        for (String code : codeToName.keySet()) {
            String parentCode = parentCodeOf(code);
            if (parentCode != null) {
                referencedAsParent.add(parentCode);
            }
        }

        // Which 1-digit classes are actually needed, given what survived
        // the filtering above.
        SortedSet<Character> rootDigitsNeeded = new TreeSet<>();
        for (String code : codeToName.keySet()) {
            rootDigitsNeeded.add(code.charAt(0));
        }

        Map<String, Long> codeToId = new HashMap<>();
        int created = 0;

        for (char digit : rootDigitsNeeded) {
            String rootCode = String.valueOf(digit);
            AccountClass accountClass = AccountClass.fromCode(rootCode);
            Long id = createOne(rootCode, ROOT_NAMES.get(digit), accountClass,
                    CLASS_DEFAULT_CATEGORY.get(accountClass),
                    AccountNature.fromAccountClass(accountClass), false, null);
            codeToId.put(rootCode, id);
            created++;
        }
        int rootAccountsCreated = created;

        List<String> orderedCodes = new ArrayList<>(codeToName.keySet());
        orderedCodes.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

        for (String code : orderedCodes) {
            String parentCode = parentCodeOf(code);
            Long parentId = codeToId.get(parentCode);
            if (parentId == null) {
                // validateIntegrity() above already guarantees every
                // parent exists -- this would mean a bug in that
                // validation, not a data gap. Fail loudly either way.
                throw new InvalidOperationException(String.format(
                        "No se pudo resolver el padre '%s' para el codigo '%s' durante la importacion.",
                        parentCode, code), "IMPORT_PUC_UNRESOLVED_PARENT");
            }

            String name = codeToName.get(code);
            AccountClass accountClass = AccountClass.fromCode(code);
            AccountCategory category = resolveCategory(effectiveType, code, name, accountClass);
            AccountNature nature = resolveNature(effectiveType, code, name, accountClass);
            boolean postingAccount = code.length() >= 6 && !referencedAsParent.contains(code);

            Long id = createOne(code, name, accountClass, category, nature, postingAccount, parentId);
            codeToId.put(code, id);
            created++;
        }

        log.info("IMPORT_PUC_DONE | tenant: {} | type: {} | accountsCreated: {} | memoSkipped: {}",
                companyId, effectiveType, created, memoSkipped);

        return new ChartTemplateImportResultDTO(effectiveType, created, rootAccountsCreated, memoSkipped);
    }

    /**
     * Rule 4 ("cero huecos"): collects EVERY code whose expected parent
     * group is missing from the template, and EVERY code whose length
     * isn't a valid PUC digit-jump (2/4/6/8/10/...), and throws a single
     * descriptive exception listing all of them if either list is
     * non-empty. Never patches or synthesizes a placeholder -- a gap or
     * a malformed code here means the seed data needs fixing at the
     * source (see sql/2026-10-30_fix_chart_account_templates_integrity.sql
     * for the 3 defects already found and fixed this way).
     */
    private void validateIntegrity(ChartTemplateType type, Map<String, String> codeToName) {
        List<String> malformedCodes = new ArrayList<>();
        List<String> orphanCodes = new ArrayList<>();

        for (String code : codeToName.keySet()) {
            if (code.length() < 2 || code.length() % 2 != 0) {
                malformedCodes.add(code);
                continue;
            }
            String parentCode = parentCodeOf(code);
            if (parentCode != null && parentCode.length() >= 2 && !codeToName.containsKey(parentCode)) {
                orphanCodes.add(code + " (esperaba el grupo '" + parentCode + "')");
            }
        }

        if (!malformedCodes.isEmpty() || !orphanCodes.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("La plantilla ").append(type)
                    .append(" tiene problemas de integridad y la importacion fue cancelada. ");
            if (!malformedCodes.isEmpty()) {
                message.append("Codigos con longitud invalida: ")
                        .append(String.join(", ", malformedCodes)).append(". ");
            }
            if (!orphanCodes.isEmpty()) {
                message.append("Codigos sin su grupo padre: ")
                        .append(String.join(", ", orphanCodes)).append(". ");
            }
            message.append("Corrija la tabla chart_account_templates antes de volver a intentar.");

            log.error("IMPORT_PUC_INTEGRITY_GAP | type: {} | malformed: {} | orphans: {}",
                    type, malformedCodes, orphanCodes);
            throw new InvalidOperationException(message.toString(), "TEMPLATE_INTEGRITY_GAP");
        }
    }

    /**
     * Which template to use -- the request's own choice if it made one,
     * otherwise whatever the company already has on file. Deliberately
     * does NOT compare the two and reject a mismatch: the caller
     * (importTemplate()) already guarantees this only runs while the
     * chart of accounts is empty, so overwriting a stale/pre-set
     * Company.chartTemplate with an explicit new choice is exactly the
     * point, not an error -- see the comment above that call site.
     */
    private ChartTemplateType resolveTemplateType(Company company, ImportChartTemplateRequest request) {
        ChartTemplateType requested = request == null ? null : request.templateType();
        ChartTemplateType effective = requested != null ? requested : company.getChartTemplate();
        if (effective == null) {
            throw new InvalidOperationException(
                    "Seleccione una plantilla de PUC (Comercial o Solidario) antes de importar.",
                    "NO_CHART_TEMPLATE_SELECTED"
            );
        }
        return effective;
    }

    /**
     * Rules 1/2 above: a specific category for every real group in the
     * account's own template, with the accumulated-depreciation/
     * amortization/depletion override for ASSET-class contra accounts.
     * A real (non-root) group this service doesn't have a mapping for
     * yet fails loudly (TEMPLATE_UNMAPPED_GROUP) instead of silently
     * defaulting -- see rule 4's "no generic fallback" spirit.
     */
    private AccountCategory resolveCategory(ChartTemplateType type, String code, String name,
                                             AccountClass accountClass) {
        List<String> depreciationPrefixes = type == ChartTemplateType.COMERCIAL
                ? COMERCIAL_ACCUMULATED_DEPRECIATION_PREFIXES
                : SOLIDARIO_ACCUMULATED_DEPRECIATION_PREFIXES;

        if (accountClass == AccountClass.ASSET) {
            for (String prefix : depreciationPrefixes) {
                if (code.startsWith(prefix)) {
                    return AccountCategory.ACCUMULATED_DEPRECIATION;
                }
            }
        }

        Map<String, AccountCategory> groupMap = type == ChartTemplateType.COMERCIAL
                ? COMERCIAL_GROUP_CATEGORY
                : SOLIDARIO_GROUP_CATEGORY;

        String groupCode = code.substring(0, 2);
        AccountCategory mapped = groupMap.get(groupCode);
        if (mapped != null) {
            return mapped;
        }

        log.error("IMPORT_PUC_UNMAPPED_GROUP | type: {} | code: {} | name: {} | group: {}",
                type, code, name, groupCode);
        throw new InvalidOperationException(String.format(
                "El grupo '%s' (cuenta '%s - %s') de la plantilla %s no tiene una categoria asignada. " +
                        "Agregue una entrada especifica antes de importar.",
                groupCode, code, name, type), "TEMPLATE_UNMAPPED_GROUP");
    }

    /**
     * Rule-based nature resolver (see class javadoc for the full
     * priority order and the data evidence behind each rule). Replaces
     * a blanket AccountNature.fromAccountClass() default with the
     * explicit (DB)/(CR) markers the seed data itself carries, plus the
     * verified ASSET-class "PROVISION"/accumulated-depreciation contra
     * rules and the REVENUE/COST/EXPENSE-class "DEVOLUCION" contra rule.
     * Takes the template type because the accumulated-depreciation-group
     * prefix check (needed for a group's CHILDREN, whose own name does
     * not repeat "acumulada" -- e.g. "159205 Construcciones y
     * edificaciones" under "1592 Depreciacion acumulada" -- confirmed by
     * inspecting the seed data) is template-specific, same as
     * resolveCategory().
     */
    private AccountNature resolveNature(ChartTemplateType type, String code, String name,
                                         AccountClass accountClass) {
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
            List<String> depreciationPrefixes = type == ChartTemplateType.COMERCIAL
                    ? COMERCIAL_ACCUMULATED_DEPRECIATION_PREFIXES
                    : SOLIDARIO_ACCUMULATED_DEPRECIATION_PREFIXES;
            for (String prefix : depreciationPrefixes) {
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

    /** Null for a 1-digit root (no parent); first char for a 2-digit code; otherwise length-2. */
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
                            AccountNature nature, boolean postingAccount, Long parentId) {
        ChartOfAccountRequest request = new ChartOfAccountRequest();
        request.setCode(code);
        request.setName(truncateName(name));
        request.setAccountClass(accountClass);
        request.setAccountCategory(category);
        request.setNature(nature);
        request.setFinancialStatement(FinancialStatement.fromAccountClass(accountClass));
        request.setPostingAccount(postingAccount);
        request.setRequiresThirdParty(false);
        request.setRequiresCostCenter(false);
        request.setActive(true);
        request.setParentId(parentId);

        return chartOfAccountService.create(request).getId();
    }

    // ChartOfAccounts.name is VARCHAR(150); the template's own names never
    // get close, but this keeps things safe if that ever changes.
    private String truncateName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() > 150 ? name.substring(0, 150) : name;
    }
}
