package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CompanyClosingDefaultsRequest;
import com.erp.erp_cloud.dto.CompanyResponseDTO;
import com.erp.erp_cloud.dto.CompanySignatureSettingsRequest;
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
                .chartTemplate(company.getChartTemplate() != null ? company.getChartTemplate().name() : null)
                .taxId(company.getTaxId())
                .verificationDigit(company.getVerificationDigit())
                .address(company.getAddress())
                .cityName(company.getCity() != null ? company.getCity().getName() : null)
                .logoDataUrl(company.getLogoDataUrl())
                .signer1Name(company.getSigner1Name())
                .signer1Role(company.getSigner1Role())
                .signer1IdNumber(company.getSigner1IdNumber())
                .signer1ProfessionalLicense(company.getSigner1ProfessionalLicense())
                .signer2Name(company.getSigner2Name())
                .signer2Role(company.getSigner2Role())
                .signer2IdNumber(company.getSigner2IdNumber())
                .signer2ProfessionalLicense(company.getSigner2ProfessionalLicense());

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

    // NEW (2026-11-09): logo + signature-block settings for legal
    // documents -- see claude/progreso-2026-11-09.md. Company-level
    // (not per-template), so this is the one place that validates and
    // saves it regardless of which legal document ends up using it.
    // Max logo size is a data-URI STRING length, not a raw-file size --
    // base64 inflates bytes by roughly 4/3.
    //
    // LOWERED (2026-11-09) from 400_000 to 200_000: the real backstop
    // against this column (and, downstream, every generated PDF) growing
    // unbounded is now CompanySettingsPage.jsx resizing the image on a
    // <canvas> to MAX_LOGO_DIMENSION_PX before it ever reaches this API --
    // a real logo re-encoded at that size lands at a few dozen KB, nowhere
    // near either limit. This server-side check stays as defense in depth
    // for whoever calls this endpoint directly (or a future client that
    // skips the resize), not as the primary control.
    private static final int MAX_LOGO_DATA_URL_LENGTH = 200_000;

    @Transactional
    public CompanyResponseDTO updateSignatureSettings(CompanySignatureSettingsRequest request) {
        Long companyId = currentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String logoDataUrl = validateLogoOrNull(request.getLogoDataUrl());

        company.setLogoDataUrl(logoDataUrl);
        company.setSigner1Name(blankToNull(request.getSigner1Name()));
        company.setSigner1Role(blankToNull(request.getSigner1Role()));
        company.setSigner1IdNumber(blankToNull(request.getSigner1IdNumber()));
        company.setSigner1ProfessionalLicense(blankToNull(request.getSigner1ProfessionalLicense()));
        company.setSigner2Name(blankToNull(request.getSigner2Name()));
        company.setSigner2Role(blankToNull(request.getSigner2Role()));
        company.setSigner2IdNumber(blankToNull(request.getSigner2IdNumber()));
        company.setSigner2ProfessionalLicense(blankToNull(request.getSigner2ProfessionalLicense()));

        companyRepository.save(company);
        return getCurrentCompany();
    }

    private String validateLogoOrNull(String logoDataUrl) {
        String value = blankToNull(logoDataUrl);
        if (value == null) {
            return null;
        }
        if (!value.startsWith("data:image/")) {
            throw new InvalidOperationException(
                    "The logo must be an embedded image (data:image/...;base64,...).", "INVALID_LOGO_FORMAT");
        }
        if (value.length() > MAX_LOGO_DATA_URL_LENGTH) {
            throw new InvalidOperationException("The logo image is too large.", "LOGO_TOO_LARGE");
        }
        return value;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
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
