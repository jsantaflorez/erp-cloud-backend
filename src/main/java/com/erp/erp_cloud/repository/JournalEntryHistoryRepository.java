package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.JournalEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryHistoryRepository extends JpaRepository<JournalEntryHistory, Long> {

    /** Versions of one entry of one company, newest first. */
    List<JournalEntryHistory> findByCompanyIdAndJournalEntryIdOrderByIdDesc(Long companyId, Long journalEntryId);
}
