package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.TaxCalculationResult;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.entity.Company;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AccountingEngineService {

    private final TaxRepository taxRepository;
    private final TenantContext companyContext;

    /**
     * Calculates the tax for a given account and base amount.
     * Useful for automatic Journal Entry generation.
     */
    public TaxCalculationResult calculateTax(ChartOfAccounts account, BigDecimal baseAmount) {
        Company company = companyContext.getCurrentCompany();

        // 1. Try to find a tax rule linked to this account
        return taxRepository.findByCompanyAndAccount(company, account)
                .map(tax -> {
                    // 2. Check if the base meets the minimum requirement
                    boolean meetsMinimum = baseAmount.abs().compareTo(tax.getMinimumBase()) >= 0;

                    BigDecimal calculatedTax = BigDecimal.ZERO;
                    if (meetsMinimum) {
                        // Math: (Base * Rate) / 100
                        calculatedTax = baseAmount.multiply(tax.getRate())
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    }

                    return TaxCalculationResult.builder()
                            .isTaxable(true)
                            .taxName(tax.getName())
                            .rate(tax.getRate())
                            .baseAmount(baseAmount)
                            .taxAmount(calculatedTax)
                            .sign(tax.getSign())
                            .accountId(tax.getAccount().getId())
                            .build();
                })
                .orElse(TaxCalculationResult.builder().isTaxable(false).build());
    }
}