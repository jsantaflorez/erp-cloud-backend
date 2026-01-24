package com.erp.erp_cloud.repository;


import com.erp.erp_cloud.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    // Standard search by name for UI usability
    List<City> findByNameContainingIgnoreCase(String name);
}