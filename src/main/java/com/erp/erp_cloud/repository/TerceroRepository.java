package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.Tercero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TerceroRepository extends JpaRepository<Tercero, Long> {

    Optional<Tercero> findByNumeroDocumento(
                        String numeroDocumento
    );
}
