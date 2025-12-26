package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.ThirdParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThirdPartyRepository extends JpaRepository<ThirdParty, Long> {

    Optional<ThirdParty> findByNumeroDocumento(
                        String numeroDocumento
    );
}
