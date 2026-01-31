package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    // TO DO add custom queries here later, like finding by Document Type
}