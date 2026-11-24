package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.JournalEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryHistoryRepository extends JpaRepository<JournalEntryHistory, Long> {

    /** Versions of one entry of one company, newest first. */
    List<JournalEntryHistory> findByCompanyIdAndJournalEntryIdOrderByIdDesc(Long companyId, Long journalEntryId);

    /** Number of saved versions per entry, for one page of the list: rows of [journalEntryId, count]. */
    @Query("SELECT h.journalEntryId, COUNT(h) FROM JournalEntryHistory h " +
            "WHERE h.companyId = :companyId AND h.journalEntryId IN :entryIds GROUP BY h.journalEntryId")
    List<Object[]> countByEntryIds(@Param("companyId") Long companyId,
                                   @Param("entryIds") java.util.Collection<Long> entryIds);

    long countByCompanyIdAndJournalEntryId(Long companyId, Long journalEntryId);
}
