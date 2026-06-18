package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.TaxCalculationResult;
import com.erp.erp_cloud.entity.ChartOfAccounts;
import com.erp.erp_cloud.repository.TaxRepository;
import com.erp.erp_cloud.service.base.TenantAwareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class AccountingEngineService extends TenantAwareService {

    private final TaxRepository taxRepository;

    /**
     * Calculates the tax for a given account and base amount.
     * Useful for automatic Journal Entry generation.
     * Aligned with the primitive ID multi-tenant optimization strategy.
     */
    public TaxCalculationResult calculateTax(ChartOfAccounts account, BigDecimal baseAmount) {
        Long companyId = currentTenantId();

        // 1. ADAPTED: Try to find a tax rule linked to this account using the optimized primitive tenant ID
        return taxRepository.findByCompanyIdAndAccount(companyId, account)
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