package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.AccountingPeriodResponseDTO;
import com.erp.erp_cloud.dto.ClosingAccountOptionDTO;
import com.erp.erp_cloud.dto.YearClosingOptionsDTO;
import com.erp.erp_cloud.entity.AccountOpeningBalance;
import com.erp.erp_cloud.entity.AccountingPeriod;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.JournalEntry;
import com.erp.erp_cloud.entity.JournalEntryItem;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.AccountOpeningBalanceRepository;
import com.erp.erp_cloud.repository.AccountingPeriodRepository;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.repository.JournalEntryRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import com.erp.erp_cloud.service.base.TenantAwareService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountingPeriodService extends TenantAwareService {

    private static final Logger log = LoggerFactory.getLogger(AccountingPeriodService.class);

    private final AccountingPeriodRepository repository;
    private final ChartOfAccountsRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountOpeningBalanceRepository openingBalanceRepository;
    private final CompanyRepository companyRepository;
    private final DocumentTypeService documentTypeService;

    @PersistenceContext
    private final EntityManager entityManager;

    // ═══════════════════════════════════════════════════════════
    // QUERY METHODS (Optimized with Your Original Tenant Context)
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findAllByCompany() {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdOrderByYearDescMonthDesc(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findClosedByCompany() {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdAndOpenFalse(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriodResponseDTO> findOpenByCompany() {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdAndOpenTrue(companyId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AccountingPeriodResponseDTO> findByYearAndMonth(Integer year, Integer month) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        return repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .map(this::mapToResponseDTO);
    }

    @Transactional(readOnly = true)
    public boolean isPeriodClosed(Integer year, Integer month) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method

        // Check Year-End Lock using optimized derived query (LIMIT 1)
        if (repository.existsByCompanyIdAndYearAndYearCloseTrue(companyId, year)) {
            return true;
        }

        return repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .map(p -> !p.isOpen())
                .orElse(false);
    }

    // ═══════════════════════════════════════════════════════════
    // YEAR-END CLOSING OPTIONS (2026-10-03)
    // ═══════════════════════════════════════════════════════════

    /**
     * Backs the two account dropdowns on the year-end closing screen:
     * the full picklist of eligible Ganancia/Pérdida accounts (posting
     * accounts of class EQUITY / Patrimonio), plus whatever the company
     * has configured as its suggested defaults (null when not
     * configured -- see Company.defaultGainAccount/defaultLossAccount).
     * The suggestion only ever pre-selects a dropdown value; it is never
     * applied on its own.
     */
    @Transactional(readOnly = true)
    public YearClosingOptionsDTO getYearClosingOptions() {
        Long companyId = TenantContext.getCurrentTenant();

        List<ClosingAccountOptionDTO> equityAccounts = accountRepository
                .findByCompanyIdAndAccountClassAndPostingAccountTrueAndActiveTrueOrderByCodeAsc(
                        companyId, AccountClass.EQUITY)
                .stream()
                .map(a -> ClosingAccountOptionDTO.builder()
                        .id(a.getId())
                        .code(a.getCode())
                        .name(a.getName())
                        .build())
                .collect(Collectors.toList());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        return YearClosingOptionsDTO.builder()
                .equityAccounts(equityAccounts)
                .suggestedGainAccountId(
                        company.getDefaultGainAccount() != null ? company.getDefaultGainAccount().getId() : null)
                .suggestedLossAccountId(
                        company.getDefaultLossAccount() != null ? company.getDefaultLossAccount().getId() : null)
                .build();
    }

    // ═══════════════════════════════════════════════════════════
    // CLOSING LOGIC (Tenant Shield Applied)
    // ═══════════════════════════════════════════════════════════

    /**
     * Standard Monthly Close.
     */
    @Transactional
    public AccountingPeriodResponseDTO closePeriod(Integer year, Integer month, String closedBy, String notes) {
        return performClose(year, month, closedBy, notes, false);
    }

    /**
     * Special Annual Fiscal Close (Locks the whole year).
     *
     * REWRITTEN (2026-10-03): this used to only flip the isYearClose flag
     * (see the git history for the old version). Per the design agreed
     * with the user, a real close now:
     *  1. Requires months 1-11 of THIS year closed (unchanged) AND, new,
     *     requires year-1 (if it exists at all) already closed --
     *     symmetric to the reverse-sequence rule reopenYear() enforces,
     *     so the chain can never go inconsistent in either direction.
     *  2. Validates the chosen Ganancia/Pérdida accounts (posting,
     *     class EQUITY, no Tercero/Centro de Costo requirement, and not
     *     the same account for both).
     *  3. Validates that no Income Statement account requiring
     *     Tercero/Centro de Costo has any movement missing one -- the
     *     per-account zeroing lines preserve both dimensions, but a gap
     *     in historical data would silently fold into a "no tercero"
     *     bucket if left unchecked.
     *  4. Generates (or, on a recalculation, annuls the previous one and
     *     regenerates) a real "CIERRE" JournalEntry that zeroes every
     *     Income Statement account per account x tercero x centro de
     *     costo, with a single consolidated contrapartida line (no
     *     tercero/CC) against whichever of Ganancia/Pérdida applies.
     *  5. Regenerates the account_opening_balances snapshot for year+1
     *     from the real Balance Sheet balances as of Dec 31 of this year.
     */
    @Transactional
    public AccountingPeriodResponseDTO closeYear(
            Integer year, String closedBy, String notes, Long gainAccountId, Long lossAccountId) {
        Long companyId = TenantContext.getCurrentTenant();
        validateYearMonth(year, 12);
        validateAllMonthsClosedBeforeYearEnd(companyId, year);
        validatePriorYearClosedIfExists(companyId, year);

        ChartOfAccounts gainAccount = resolveClosingEquityAccount(gainAccountId, companyId);
        ChartOfAccounts lossAccount = resolveClosingEquityAccount(lossAccountId, companyId);
        if (gainAccount.getId().equals(lossAccount.getId())) {
            throw new InvalidOperationException(
                    "Las cuentas de Ganancia y Pérdida deben ser diferentes.",
                    "GAIN_LOSS_ACCOUNTS_MUST_DIFFER");
        }

        LocalDate cutoff = LocalDate.of(year, 12, 31);
        List<Object[]> resultAccountRows =
                journalEntryRepository.getYearEndBalancesByAccount(companyId, cutoff, true);

        validateThirdPartyAndCostCenterIntegrityForClosing(companyId, resultAccountRows);

        // Single fetch-or-create of the period entity -- deliberately NOT
        // delegated to performClose() + a second lookup afterwards. A
        // second findByCompanyIdAndYearAndMonth call here would, in a real
        // database, see the row performClose just saved; it was only a
        // mocked-repository test artifact that exposed this as a real
        // correctness risk (same mock, same stubbed answer, called twice
        // -- the second call doesn't "see" the save a mock made in
        // between). Fetching once and reusing the same managed entity for
        // every field this method sets avoids depending on that at all.
        AccountingPeriod period = findOrCreatePeriodEntity(companyId, year, 12);

        // Recalculation path (year was reopened, corrected, and is being
        // closed again): annul the previous closing entry before a new
        // one is generated -- never leave two live closing entries for
        // the same year.
        if (period.getClosingEntry() != null) {
            annulClosingEntry(period.getClosingEntry(), closedBy, year);
        }

        JournalEntry closingEntry =
                buildAndSaveClosingEntry(companyId, year, resultAccountRows, gainAccount, lossAccount);

        period.setOpen(false);
        period.setYearClose(true);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        period.setClosingNotes(notes);
        period.setGainAccount(gainAccount);
        period.setLossAccount(lossAccount);
        period.setClosingEntry(closingEntry);

        AccountingPeriod saved = repository.save(period);

        regenerateOpeningBalancesForNextYear(companyId, year);

        return mapToResponseDTO(saved);
    }

    /**
     * Shared get-or-create for a period record, factored out of
     * performClose() so closeYear() can fetch it once, set every field
     * it needs (including the new gainAccount/lossAccount/closingEntry),
     * and save once -- see the comment in closeYear() for why a second
     * fetch after an intermediate save is deliberately avoided.
     */
    private AccountingPeriod findOrCreatePeriodEntity(Long companyId, Integer year, Integer month) {
        return repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .orElseGet(() -> {
                    AccountingPeriod newP = new AccountingPeriod();
                    Company proxyCompany = entityManager.getReference(Company.class, companyId);
                    newP.setCompany(proxyCompany);
                    newP.setYear(year);
                    newP.setMonth(month);
                    return newP;
                });
    }

    /**
     * Ensures months 1-11 of the fiscal year are already individually
     * closed before the year can be sealed. A month with no period record
     * at all counts as "not closed" here -- stricter than the general
     * validateDateIsOpen()/isPeriodClosed() convention elsewhere in this
     * service, where a missing record defaults to open, because year-end
     * closing must be an explicit, reviewed step for every month.
     */
    private void validateAllMonthsClosedBeforeYearEnd(Long companyId, Integer year) {
        List<AccountingPeriod> periods = repository.findByCompanyIdAndYear(companyId, year);

        List<Integer> notClosed = IntStream.rangeClosed(1, 11)
                .filter(month -> periods.stream()
                        .noneMatch(p -> p.getMonth().equals(month) && !p.isOpen()))
                .boxed()
                .collect(Collectors.toList());

        if (!notClosed.isEmpty()) {
            throw new InvalidOperationException(
                    "Cannot close fiscal year " + year + ": months " + notClosed
                            + " must be closed individually first.",
                    "MONTHS_NOT_CLOSED_BEFORE_YEAR_END"
            );
        }
    }

    /**
     * NEW (2026-10-03): symmetric counterpart to the reverse-sequence
     * rule reopenYear() enforces. Closing year N while year N-1 is still
     * open would compute N's opening-balance snapshot off a prior year
     * that can still change -- exactly the inconsistency the reopen rule
     * protects against in the other direction. A year with no period
     * record at all (company's very first fiscal year) has nothing to
     * require here.
     */
    private void validatePriorYearClosedIfExists(Long companyId, Integer year) {
        Integer priorYear = year - 1;
        List<AccountingPeriod> priorYearPeriods = repository.findByCompanyIdAndYear(companyId, priorYear);
        if (priorYearPeriods.isEmpty()) {
            return;
        }
        boolean priorYearClosed = priorYearPeriods.stream().anyMatch(AccountingPeriod::isYearClose);
        if (!priorYearClosed) {
            throw new InvalidOperationException(
                    "No se puede cerrar el año " + year + ": el año " + priorYear
                            + " debe cerrarse primero.",
                    "PRIOR_YEAR_NOT_CLOSED");
        }
    }

    /**
     * Validates a chosen Ganancia/Pérdida account: must be a posting
     * (auxiliary) account of class EQUITY (3 / Patrimonio) belonging to
     * this company, and must not require Tercero or Centro de Costo --
     * its closing line is always a single consolidated amount, it can
     * never carry either dimension (confirmed with the user 2026-10-03).
     */
    private ChartOfAccounts resolveClosingEquityAccount(Long accountId, Long companyId) {
        if (accountId == null) {
            throw new InvalidOperationException(
                    "Debe seleccionar la cuenta de Ganancia y la cuenta de Pérdida.",
                    "CLOSING_ACCOUNT_REQUIRED");
        }
        ChartOfAccounts account = accountRepository.findById(accountId)
                .filter(a -> a.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getAccountClass() != AccountClass.EQUITY || !account.isPostingAccount()) {
            throw new InvalidOperationException(
                    "La cuenta " + account.getCode()
                            + " debe ser una cuenta de movimiento de Patrimonio (clase 3).",
                    "INVALID_CLOSING_ACCOUNT");
        }
        if (account.isRequiresThirdParty() || account.isRequiresCostCenter()) {
            throw new InvalidOperationException(
                    "La cuenta " + account.getCode()
                            + " no puede exigir Tercero ni Centro de Costo: la línea de cierre contra"
                            + " esta cuenta siempre es consolidada.",
                    "CLOSING_ACCOUNT_CANNOT_REQUIRE_DIMENSIONS");
        }
        return account;
    }

    /**
     * Pre-check requested by the user (2026-10-03): stops the automatic
     * close, listing the offending accounts, when a result account
     * (closesAtYearEnd = true) that requires Tercero or Centro de Costo
     * has at least one movement missing it. The per-account zeroing
     * lines always carry forward whatever tercero/costCenter each row
     * already has (see buildAndSaveClosingEntry) -- this check protects
     * against a gap in HISTORICAL data (e.g. an old import) silently
     * being folded into a "sin tercero" bucket instead of being fixed.
     */
    private void validateThirdPartyAndCostCenterIntegrityForClosing(Long companyId, List<Object[]> resultAccountRows) {
        var accountsById = accountRepository.findByCompanyIdAndActiveTrueOrderByCodeAsc(companyId)
                .stream()
                .collect(Collectors.toMap(ChartOfAccounts::getId, a -> a));

        Set<String> missingThirdParty = new TreeSet<>();
        Set<String> missingCostCenter = new TreeSet<>();

        for (Object[] row : resultAccountRows) {
            Long accountId = (Long) row[0];
            Long thirdPartyId = (Long) row[1];
            Long costCenterId = (Long) row[2];
            ChartOfAccounts account = accountsById.get(accountId);
            if (account == null) {
                continue;
            }
            if (account.isRequiresThirdParty() && thirdPartyId == null) {
                missingThirdParty.add(account.getCode() + " - " + account.getName());
            }
            if (account.isRequiresCostCenter() && costCenterId == null) {
                missingCostCenter.add(account.getCode() + " - " + account.getName());
            }
        }

        if (!missingThirdParty.isEmpty() || !missingCostCenter.isEmpty()) {
            StringBuilder msg = new StringBuilder("No se puede cerrar el año automáticamente. ");
            if (!missingThirdParty.isEmpty()) {
                msg.append("Cuentas que exigen Tercero con movimientos sin tercero asignado: ")
                        .append(String.join("; ", missingThirdParty))
                        .append(". ");
            }
            if (!missingCostCenter.isEmpty()) {
                msg.append("Cuentas que exigen Centro de Costo con movimientos sin centro de costo asignado: ")
                        .append(String.join("; ", missingCostCenter))
                        .append(".");
            }
            throw new InvalidOperationException(
                    msg.toString(), "RESULT_ACCOUNTS_MISSING_THIRD_PARTY_OR_COST_CENTER");
        }
    }

    /**
     * Builds and saves the system-generated "CIERRE" entry: one zeroing
     * line per (account, tercero, centro de costo) combination with a
     * non-zero balance among the Income Statement accounts, preserving
     * both dimensions exactly as the ledger already has them, plus a
     * single consolidated contrapartida line (no tercero/CC) crediting
     * gainAccount or debiting lossAccount depending on the computed net
     * result. Returns null (posts nothing) when there is genuinely no
     * Income Statement activity to close for the year.
     */
    private JournalEntry buildAndSaveClosingEntry(
            Long companyId, Integer year, List<Object[]> resultAccountRows,
            ChartOfAccounts gainAccount, ChartOfAccounts lossAccount) {

        DocumentType docType = documentTypeService.findOrCreateClosingDocumentType(companyId);

        JournalEntry entry = new JournalEntry();
        entry.setDocumentType(docType);
        entry.setEntryDate(LocalDate.of(year, 12, 31));
        entry.setDescription("Cierre de Año Fiscal " + year);
        entry.setCompany(entityManager.getReference(Company.class, companyId));

        Long nextNumber = documentTypeService.getNextConsecutive(docType.getId());
        entry.setConsecutive(nextNumber);
        entry.setDocumentNumber(
                (docType.getPrefix() != null && !docType.getPrefix().isBlank())
                        ? docType.getPrefix().trim() + "-" + nextNumber
                        : nextNumber.toString());

        // Positive = net Debit balance across Income Statement accounts
        // (net expense/cost), negative = net Credit balance (net
        // revenue). netResult below is -sum(balance): positive means
        // Ganancia, negative means Pérdida -- see the session's design
        // discussion (2026-10-03) for the full derivation.
        BigDecimal netResult = BigDecimal.ZERO;

        for (Object[] row : resultAccountRows) {
            Long accountId = (Long) row[0];
            Long thirdPartyId = (Long) row[1];
            Long costCenterId = (Long) row[2];
            BigDecimal debit = ((BigDecimal) row[3]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal credit = ((BigDecimal) row[4]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal balance = debit.subtract(credit);

            if (balance.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            ChartOfAccounts account = entityManager.getReference(ChartOfAccounts.class, accountId);

            JournalEntryItem item = new JournalEntryItem();
            item.setAccount(account);
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                // Net debit balance (e.g. gasto/costo) -> credit to zero it.
                item.setDebit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                item.setCredit(balance);
            } else {
                // Net credit balance (e.g. ingreso) -> debit to zero it.
                item.setDebit(balance.negate());
                item.setCredit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            item.setDescription("Cierre " + year + " - " + account.getCode());
            if (thirdPartyId != null) {
                item.setThirdParty(entityManager.getReference(ThirdParty.class, thirdPartyId));
            }
            if (costCenterId != null) {
                item.setCostCenter(entityManager.getReference(CostCenter.class, costCenterId));
            }
            entry.addItem(item);

            netResult = netResult.subtract(balance);
        }

        netResult = netResult.setScale(2, RoundingMode.HALF_UP);

        if (netResult.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntryItem resultItem = new JournalEntryItem();
            resultItem.setAccount(gainAccount);
            resultItem.setDebit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            resultItem.setCredit(netResult);
            resultItem.setDescription("Utilidad del Ejercicio " + year);
            entry.addItem(resultItem);
        } else if (netResult.compareTo(BigDecimal.ZERO) < 0) {
            JournalEntryItem resultItem = new JournalEntryItem();
            resultItem.setAccount(lossAccount);
            resultItem.setDebit(netResult.negate());
            resultItem.setCredit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            resultItem.setDescription("Pérdida del Ejercicio " + year);
            entry.addItem(resultItem);
        }
        // netResult == 0: no result line needed, the zeroing lines alone
        // already balance (total debit == total credit among them).

        if (entry.getItems().isEmpty()) {
            log.info("No Income Statement activity to close for year {} (company {}); no entry generated.",
                    year, companyId);
            return null;
        }

        JournalEntry saved = journalEntryRepository.save(entry);
        log.info("Generated closing entry {} for fiscal year {} (company {}), net result: {}",
                saved.getDocumentNumber(), year, companyId, netResult);
        return saved;
    }

    /**
     * Annuls a closing entry the same way JournalEntryService.annul()
     * neutralizes any other entry (zeroes item amounts in place, flags
     * annulled) -- so every query elsewhere in this repository that
     * already relies on "annulled entries net to zero on their own"
     * keeps working unchanged, with no separate annulled filter needed.
     */
    private void annulClosingEntry(JournalEntry entry, String actor, Integer year) {
        if (entry.isAnnulled()) {
            return;
        }
        entry.setAnnulled(true);
        entry.setAnnulledAt(LocalDateTime.now());
        entry.setAnnulledBy(actor);
        entry.setAnnulmentReason("Recálculo del cierre del año " + year);

        for (JournalEntryItem item : entry.getItems()) {
            item.setDebit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            item.setCredit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        String prefix = "[ANULADO] ";
        if (entry.getDescription() != null && !entry.getDescription().startsWith(prefix)) {
            entry.setDescription(prefix + entry.getDescription());
        }

        journalEntryRepository.save(entry);
    }

    /**
     * Regenerates the account_opening_balances snapshot for year+1 from
     * the real Balance Sheet balances (closesAtYearEnd = false) as of
     * Dec 31 of the year just closed. Always wipes year+1's existing
     * rows first (AccountOpeningBalanceRepository.deleteByCompanyIdAndYear)
     * so an account that no longer carries a balance never lingers as
     * stale data. balance is stored as debit-minus-credit (debit-positive
     * convention) -- same raw convention the live-sum opening-balance
     * queries elsewhere already use; a future consumer applies CASE on
     * account.nature exactly like those queries do, nothing new here.
     */
    private void regenerateOpeningBalancesForNextYear(Long companyId, Integer year) {
        Integer nextYear = year + 1;
        LocalDate cutoff = LocalDate.of(year, 12, 31);

        List<Object[]> rows = journalEntryRepository.getYearEndBalancesByAccount(companyId, cutoff, false);

        openingBalanceRepository.deleteByCompanyIdAndYear(companyId, nextYear);

        List<AccountOpeningBalance> toSave = new ArrayList<>();
        for (Object[] row : rows) {
            Long accountId = (Long) row[0];
            Long thirdPartyId = (Long) row[1];
            Long costCenterId = (Long) row[2];
            BigDecimal debit = ((BigDecimal) row[3]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal credit = ((BigDecimal) row[4]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal balance = debit.subtract(credit);

            if (balance.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            AccountOpeningBalance ob = new AccountOpeningBalance();
            ob.setCompany(entityManager.getReference(Company.class, companyId));
            ob.setYear(nextYear);
            ob.setAccount(entityManager.getReference(ChartOfAccounts.class, accountId));
            if (thirdPartyId != null) {
                ob.setThirdParty(entityManager.getReference(ThirdParty.class, thirdPartyId));
            }
            if (costCenterId != null) {
                ob.setCostCenter(entityManager.getReference(CostCenter.class, costCenterId));
            }
            ob.setBalance(balance);
            toSave.add(ob);
        }

        openingBalanceRepository.saveAll(toSave);
        log.info("Regenerated {} opening-balance rows for year {} (company {})",
                toSave.size(), nextYear, companyId);
    }

    private AccountingPeriodResponseDTO performClose(Integer year, Integer month, String closedBy, String notes, boolean isYearEnd) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        validateYearMonth(year, month);

        AccountingPeriod period = findOrCreatePeriodEntity(companyId, year, month);

        period.setOpen(false);
        period.setYearClose(isYearEnd);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        period.setClosingNotes(notes);

        return mapToResponseDTO(repository.save(period));
    }

    @Transactional
    public AccountingPeriodResponseDTO reopenPeriod(Integer year, Integer month, String reopenedBy, String notes) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method
        validateYearMonth(year, month);

        AccountingPeriod period = repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .orElseThrow(() -> new ResourceNotFoundException("Period not found under this company"));

        period.setOpen(true);
        period.setYearClose(false);
        period.setReopenedAt(LocalDateTime.now());
        period.setReopenedBy(reopenedBy);
        period.setReopeningNotes(notes);

        return mapToResponseDTO(repository.save(period));
    }

    /**
     * Reopens a full fiscal year by removing the YearClose seal.
     *
     * REWRITTEN (2026-10-03): now enforces the reverse-sequence rule
     * agreed with the user -- year N cannot be reopened while year N+1
     * is still closed (if it exists at all), the exact mirror of the
     * forward rule closeYear() enforces. Also annuls the year's closing
     * entry (never deletes it), since reopening means the numbers it
     * posted are no longer trustworthy until the year is closed again.
     */
    @Transactional
    public void reopenYear(Integer year, String reopenedBy, String notes) {
        Long companyId = TenantContext.getCurrentTenant(); // FIXED: Matches your context method

        validateNextYearOpenBeforeReopen(companyId, year);

        List<AccountingPeriod> yearPeriods = repository.findByCompanyIdAndYear(companyId, year);

        yearPeriods.stream()
                .filter(AccountingPeriod::isYearClose)
                .forEach(period -> {
                    period.setYearClose(false);
                    period.setReopenedAt(LocalDateTime.now());
                    period.setReopenedBy(reopenedBy);
                    period.setReopeningNotes("Annual unseal: " + notes);
                    if (period.getClosingEntry() != null) {
                        annulClosingEntry(period.getClosingEntry(), reopenedBy, year);
                    }
                });

        repository.saveAll(yearPeriods);
        log.info("Fiscal Year {} has been unsealed by {} for company {}", year, reopenedBy, companyId);
    }

    /**
     * NEW (2026-10-03): the reverse-sequence rule itself. Year N+1 with
     * no period record at all means nothing blocks reopening N.
     */
    private void validateNextYearOpenBeforeReopen(Long companyId, Integer year) {
        Integer nextYear = year + 1;
        List<AccountingPeriod> nextYearPeriods = repository.findByCompanyIdAndYear(companyId, nextYear);
        if (nextYearPeriods.isEmpty()) {
            return;
        }
        boolean nextYearClosed = nextYearPeriods.stream().anyMatch(AccountingPeriod::isYearClose);
        if (nextYearClosed) {
            throw new InvalidOperationException(
                    "Para reabrir el año " + year + ", primero debe reabrir el año " + nextYear + ".",
                    "NEXT_YEAR_MUST_BE_OPEN_BEFORE_REOPEN");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION (Used by JournalEntryService)
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates if a specific transaction date is within an open period.
     */
    @Transactional(readOnly = true)
    public void validateDateIsOpen(LocalDate date, Long companyId) {
        if (date == null) throw new InvalidOperationException("Date is required", "PERIOD_DATE_REQUIRED");

        int year = date.getYear();
        int month = date.getMonthValue();

        if (repository.existsByCompanyIdAndYearAndYearCloseTrue(companyId, year)) {
            throw new InvalidOperationException("The Fiscal Year " + year + " is CLOSED.", "FISCAL_YEAR_CLOSED");
        }

        repository.findByCompanyIdAndYearAndMonth(companyId, year, month)
                .ifPresent(p -> {
                    if (!p.isOpen()) {
                        throw new InvalidOperationException("The period " + year + "-" + month + " is CLOSED.", "ACCOUNTING_PERIOD_CLOSED");
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private void validateYearMonth(Integer year, Integer month) {
        if (year < 1900 || year > 2100) throw new InvalidOperationException("Invalid year", "INVALID_YEAR");
        if (month < 1 || month > 12) throw new InvalidOperationException("Invalid month", "INVALID_MONTH");
    }

    private AccountingPeriodResponseDTO mapToResponseDTO(AccountingPeriod entity) {
        // FIX: this used to silently drop periodCode, closingNotes,
        // reopenedAt, reopenedBy and reopeningNotes even though the DTO
        // declares all of them -- callers (and the upcoming Periodos
        // Contables screen) need the full audit trail, not just who/when
        // it was last closed.
        AccountingPeriodResponseDTO.AccountingPeriodResponseDTOBuilder dto = AccountingPeriodResponseDTO.builder()
                .id(entity.getId())
                .year(entity.getYear())
                .month(entity.getMonth())
                .periodCode(entity.getPeriodCode())
                .isOpen(entity.isOpen())
                .isYearClose(entity.isYearClose())
                .closedAt(entity.getClosedAt())
                .closedBy(entity.getClosedBy())
                .closingNotes(entity.getClosingNotes())
                .reopenedAt(entity.getReopenedAt())
                .reopenedBy(entity.getReopenedBy())
                .reopeningNotes(entity.getReopeningNotes());

        if (entity.getGainAccount() != null) {
            dto.gainAccountId(entity.getGainAccount().getId())
                    .gainAccountCode(entity.getGainAccount().getCode())
                    .gainAccountName(entity.getGainAccount().getName());
        }
        if (entity.getLossAccount() != null) {
            dto.lossAccountId(entity.getLossAccount().getId())
                    .lossAccountCode(entity.getLossAccount().getCode())
                    .lossAccountName(entity.getLossAccount().getName());
        }
        if (entity.getClosingEntry() != null) {
            dto.closingEntryId(entity.getClosingEntry().getId())
                    .closingEntryDocumentNumber(entity.getClosingEntry().getDocumentNumber());
        }

        return dto.build();
    }
}
