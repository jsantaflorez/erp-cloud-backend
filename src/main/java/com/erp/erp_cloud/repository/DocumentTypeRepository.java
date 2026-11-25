package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.entity.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    // ═══════════════════════════════════════════════════════════
    // ADAPTED TENANT METHODS (Primitive ID-based for optimization)
    // ═══════════════════════════════════════════════════════════

    // Retrieves all active document types for a specific tenant ID
    List<DocumentType> findByCompanyIdAndActiveTrue(Long companyId);

    // Finds a document type by company ID and code
    Optional<DocumentType> findByCompanyIdAndCode(Long companyId, String code);

    // ADAPTED: Checks unique constraints using primitive Long company ID
    boolean existsByCompanyIdAndCode(Long companyId, String code);

    // ADAPTED: Searches using primitive Long company ID to avoid object mapping
    List<DocumentType> findByCompanyIdAndNameContainingIgnoreCase(Long companyId, String name);

    /**
     * CRITICAL: Pessimistic Lock used to ensure strict consecutive increments
     * without generating race conditions under high concurrency environments.
     * ADAPTED: Enforces tenant isolation during locking to prevent cross-tenant access.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dt FROM DocumentType dt WHERE dt.id = :id AND dt.company.id = :companyId")
    Optional<DocumentType> findByIdWithLockAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    // ═══════════════════════════════════════════════════════════
    // LEGACY METHODS (Object-based for backward compatibility)
    // ═══════════════════════════════════════════════════════════

    boolean existsByCompanyAndCode(Company company, String code);

    List<DocumentType> findByCompanyAndNameContainingIgnoreCase(Company company, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dt FROM DocumentType dt WHERE dt.id = :id")
    Optional<DocumentType> findByIdWithLock(@Param("id") Long id);

    // ═══════════════════════════════════════════════════════════
    // Consecutive (fix 2026-11-24, found seeding volume for Fase 4)
    // ═══════════════════════════════════════════════════════════
    //
    // findByIdWithLock() did take the row lock (SELECT ... FOR UPDATE), but
    // when the DocumentType was ALREADY in the persistence context -- and
    // JournalEntryService.create() always loads it first, to validate it --
    // Hibernate returns that managed instance with its STALE
    // current_consecutive instead of the value it just read. Two documents of
    // the same type saved at the same time got the same number and the second
    // one failed with 409 DUPLICATE_VALUE (the unique key caught it, so no
    // duplicate ever reached the database).
    //
    // A database-side UPDATE always works on the latest committed row and
    // locks it until commit; the SELECT right after, in the same transaction,
    // sees this transaction's own update. No stale cache in between.

    /** current_consecutive + 1 on the row of this company; returns rows updated (0 = not found / other company). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE DocumentType dt SET dt.currentConsecutive = dt.currentConsecutive + 1 " +
            "WHERE dt.id = :id AND dt.company.id = :companyId")
    int incrementConsecutive(@Param("id") Long id, @Param("companyId") Long companyId);

    /** Current value read from the database (scalar: bypasses the persistence context). */
    @Query("SELECT dt.currentConsecutive FROM DocumentType dt WHERE dt.id = :id AND dt.company.id = :companyId")
    Long findCurrentConsecutive(@Param("id") Long id, @Param("companyId") Long companyId);
}