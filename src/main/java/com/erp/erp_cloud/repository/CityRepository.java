package com.erp.erp_cloud.repository;


import com.erp.erp_cloud.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    // Standard search by name for UI usability
    List<City> findByNameContainingIgnoreCase(String name);

    // NEW (2026-11-01): needed by SiewinThirdPartyImportService to
    // validate a third party's COD.MUNICIPIO (normalized to 5 digits,
    // zero-padded -- DANE code) against this precargada catalog. `code`
    // is already @Column(unique = true) on City, so this is a direct
    // lookup, no ambiguity possible.
    Optional<City> findByCode(String code);
}