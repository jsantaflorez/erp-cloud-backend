package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CompanyClosingDefaultsRequest;
import com.erp.erp_cloud.dto.CompanyResponseDTO;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.enums.AccountClass;
import com.erp.erp_cloud.exception.InvalidOperationException;
import com.erp.erp_cloud.exception.ResourceNotFoundException;
import com.erp.erp_cloud.repository.ChartOfAccountsRepository;
import com.erp.erp_cloud.repository.CompanyRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NEW (2026-09-10): first Company-facing service in the app -- there is no
 * Company CRUD yet (companies are provisioned directly in the database),
 * but the frontend needs a real way to read the current tenant's own data
 * instead of the hardcoded stand-in it uses today (see
 * erp-cloud-frontend/src/services/tenantSession.js). Started minimal:
 * just what's needed to know whether -- and which -- chart-of-accounts
 * template applies, so ChartOfAccountPage can decide whether to offer
 * name suggestions. Grows into a real Company settings endpoint later
 * without needing a different shape.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService extends TenantAwareService {

    private final CompanyRepository companyRepository;
    private final ChartOfAccountsRepository accountRepository;

    public CompanyResponseDTO getCurrentCompany() {
        Company company = currentCompany();
        CompanyResponseDTO.CompanyResponseDTOBuilder dto = CompanyResponseDTO.builder()
                .id(company.getId())
                .legalName(company.getLegalName())
                .tradeName(company.getTradeName())
                .chartTemplate(company.getChartTemplate() != null ? company.getChartTemplate().name() : null);

        ChartOfAccounts gain = company.getDefaultGainAccount();
        if (gain != null) {
            dto.defaultGainAccountId(gain.getId())
                    .defaultGainAccountCode(gain.getCode())
                    .defaultGainAccountName(gain.getName());
        }
        ChartOfAccounts loss = company.getDefaultLossAccount();
        if (loss != null) {
            dto.defaultLossAccountId(loss.getId())
                    .defaultLossAccountCode(loss.getCode())
                    .defaultLossAccountName(loss.getName());
        }

        return dto.build();
    }

    /**
     * NEW (2026-10-03): sets (or clears, via null) the company's
     * suggested Ganancia/Pérdida accounts for the year-end closing
     * screen. Both validated against the same restriction as the closing
     * screen itself -- posting account, class EQUITY (3), belonging to
     * this company -- so a bad default can never be saved in the first
     * place.
     */
    @Transactional
    public CompanyResponseDTO updateClosingDefaults(CompanyClosingDefaultsRequest request) {
        Long companyId = currentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        ChartOfAccounts gain = resolveEquityAccountOrNull(request.getDefaultGainAccountId(), companyId);
        ChartOfAccounts loss = resolveEquityAccountOrNull(request.getDefaultLossAccountId(), companyId);

        // NEW (2026-10-06): the two suggestions are only ever useful if
        // they point at different accounts -- closeYear() already
        // enforces this for the accounts actually confirmed for a given
        // year (GAIN_LOSS_ACCOUNTS_MUST_DIFFER), but nothing stopped the
        // company-level suggestion itself from being saved with the same
        // account in both fields, which would just get rejected later at
        // closing time anyway. Same stable error code reused here since
        // it's the identical rule, just checked one step earlier; a null
        // in either field (no suggestion configured yet) never triggers
        // this -- only two concrete, equal account ids do.
        if (gain != null && loss != null && gain.getId().equals(loss.getId())) {
            throw new InvalidOperationException(
                    "Las cuentas de Ganancia y Pérdida deben ser diferentes.",
                    "GAIN_LOSS_ACCOUNTS_MUST_DIFFER");
        }

        company.setDefaultGainAccount(gain);
        company.setDefaultLossAccount(loss);

        companyRepository.save(company);
        return getCurrentCompany();
    }

    private ChartOfAccounts resolveEquityAccountOrNull(Long accountId, Long companyId) {
        if (accountId == null) {
            return null;
        }
        ChartOfAccounts account = accountRepository.findById(accountId)
                .filter(a -> a.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getAccountClass() != AccountClass.EQUITY || !account.isPostingAccount()) {
            throw new InvalidOperationException(
                    "Account " + account.getCode() + " must be a posting (auxiliary) account of class Patrimonio (3).",
                    "INVALID_CLOSING_ACCOUNT");
        }
        return account;
    }
}
