package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.AccountOpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountOpeningBalanceRepository extends JpaRepository<AccountOpeningBalance, Long> {

    List<AccountOpeningBalance> findByCompanyIdAndYear(Long companyId, Integer year);

    /**
     * Wipes a company's opening-balance snapshot for a year before it gets
     * regenerated from scratch (AccountingPeriodService.closeYear). Always
     * called right before a batch insert of the freshly computed rows --
     * never used on its own -- so an account that no longer carries a
     * balance simply doesn't get a new row, instead of lingering as stale
     * data from a previous calculation.
     */
    @Modifying
    @Query("DELETE FROM AccountOpeningBalance b WHERE b.company.id = :companyId AND b.year = :year")
    void deleteByCompanyIdAndYear(@Param("companyId") Long companyId, @Param("year") Integer year);
}
