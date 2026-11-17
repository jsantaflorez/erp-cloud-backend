package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ChartOfAccountResetPreviewDTO;
import com.erp.erp_cloud.dto.ChartOfAccountResetRequest;
import com.erp.erp_cloud.dto.ChartOfAccountResetResultDTO;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * NEW (2026-11-17, user request): delete the whole chart of accounts of
 * the current company so it can be imported again -- the real case was a
 * PUC migrated from an old SIEWIN copy that no longer matches the opening
 * balance file.
 *
 * Only allowed while NOTHING has been recorded against the accounts. Every
 * table with a foreign key to chart_of_accounts (see V1__baseline.sql) is
 * covered:
 *  - BLOCKERS (never touched, reset refused): journal_entry_items (any
 *    entry, including annulled ones), account_opening_balances, closed
 *    accounting_periods (gain/loss account).
 *  - DELETED with the plan: taxes (their account is NOT NULL; the only
 *    table referencing taxes is journal_entry_items, already a blocker,
 *    and taxes can't be deleted from the UI -- only deactivated). Listed
 *    in the preview so the user knows they must create them again.
 *  - CLEARED automatically (configuration, set again after re-importing):
 *    document_types.default_account_id, t_companies.default_gain/loss
 *    account, t_companies.chart_template.
 *  - chart_of_accounts itself (parent_id first, then the rows).
 * All in one transaction: either everything is cleared or nothing is.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartOfAccountResetService extends TenantAwareService {

    public static final String CONFIRMATION_WORD = "BORRAR";

    private final EntityManager entityManager;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public ChartOfAccountResetPreviewDTO preview() {
        Long companyId = currentTenantId();
        Company company = loadCompany(companyId);
        List<String> blockers = findBlockers(companyId);
        return new ChartOfAccountResetPreviewDTO(
                count("SELECT COUNT(*) FROM chart_of_accounts WHERE company_id = ?1", companyId),
                blockers.isEmpty(),
                blockers,
                count("SELECT COUNT(*) FROM document_types WHERE company_id = ?1 AND default_account_id IS NOT NULL", companyId),
                company.getDefaultGainAccount() != null || company.getDefaultLossAccount() != null,
                taxLabels(companyId));
    }

    @Transactional
    public ChartOfAccountResetResultDTO reset(ChartOfAccountResetRequest request, String username) {
        if (request == null || !CONFIRMATION_WORD.equals(request.confirmation())) {
            throw new InvalidOperationException(
                    "Confirmacion invalida: escriba BORRAR para confirmar.", "RESET_CONFIRMATION_REQUIRED");
        }
        Long companyId = currentTenantId();
        Company company = loadCompany(companyId);

        List<String> blockers = findBlockers(companyId);
        if (!blockers.isEmpty()) {
            throw new InvalidOperationException(
                    "No se puede borrar el plan de cuentas: ya hay informacion registrada con estas cuentas.",
                    "CHART_OF_ACCOUNTS_RESET_BLOCKED", blockers);
        }

        boolean companyDefaultsCleared = company.getDefaultGainAccount() != null || company.getDefaultLossAccount() != null;
        company.setDefaultGainAccount(null);
        company.setDefaultLossAccount(null);
        company.setChartTemplate(null);
        companyRepository.saveAndFlush(company);

        long taxesDeleted = entityManager.createNativeQuery("DELETE FROM taxes WHERE company_id = ?1")
                .setParameter(1, companyId).executeUpdate();
        long documentTypesCleared = entityManager.createNativeQuery(
                        "UPDATE document_types SET default_account_id = NULL WHERE company_id = ?1 AND default_account_id IS NOT NULL")
                .setParameter(1, companyId).executeUpdate();
        entityManager.createNativeQuery("UPDATE chart_of_accounts SET parent_id = NULL WHERE company_id = ?1")
                .setParameter(1, companyId).executeUpdate();
        long accountsDeleted = entityManager.createNativeQuery("DELETE FROM chart_of_accounts WHERE company_id = ?1")
                .setParameter(1, companyId).executeUpdate();
        // Nothing cached in this persistence context may still point at a deleted account.
        entityManager.clear();

        log.warn("CHART_OF_ACCOUNTS_RESET | tenant: {} | user: {} | accountsDeleted: {} | taxesDeleted: {} | documentTypesCleared: {} | companyDefaultsCleared: {}",
                companyId, username, accountsDeleted, taxesDeleted, documentTypesCleared, companyDefaultsCleared);
        return new ChartOfAccountResetResultDTO(accountsDeleted, documentTypesCleared, companyDefaultsCleared, taxesDeleted);
    }

    /** Human-readable reasons the reset is not allowed; empty when it is. */
    private List<String> findBlockers(Long companyId) {
        List<String> blockers = new ArrayList<>();
        long entries = count("SELECT COUNT(DISTINCT journal_entry_id) FROM journal_entry_items WHERE company_id = ?1", companyId);
        if (entries > 0) {
            blockers.add("Hay " + entries + " comprobante(s) contable(s) registrados (incluidos los anulados).");
        }
        long legacyOpening = count("SELECT COUNT(*) FROM account_opening_balances WHERE company_id = ?1", companyId);
        if (legacyOpening > 0) {
            blockers.add("Hay " + legacyOpening + " saldo(s) iniciales registrados por cuenta.");
        }
        long periods = count("SELECT COUNT(*) FROM accounting_periods WHERE company_id = ?1 "
                + "AND (gain_account_id IS NOT NULL OR loss_account_id IS NOT NULL)", companyId);
        if (periods > 0) {
            blockers.add("Hay " + periods + " periodo(s) cerrados con cuentas de cierre.");
        }
        return blockers;
    }

    private List<String> taxLabels(Long companyId) {
        @SuppressWarnings("unchecked")
        List<Object[]> taxes = entityManager.createNativeQuery(
                        "SELECT code, name FROM taxes WHERE company_id = ?1 ORDER BY code")
                .setParameter(1, companyId).getResultList();
        List<String> labels = new ArrayList<>();
        for (Object[] t : taxes) {
            labels.add(t[0] + " - " + t[1]);
        }
        return labels;
    }

    private long count(String sql, Long companyId) {
        Object result = entityManager.createNativeQuery(sql).setParameter(1, companyId).getSingleResult();
        return result == null ? 0L : ((Number) result).longValue();
    }

    private Company loadCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new InvalidOperationException(
                        "No se encontro la compania actual.", "COMPANY_NOT_FOUND"));
    }
}
