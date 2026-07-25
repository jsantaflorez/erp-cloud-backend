package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.CityResponseDTO;
import com.erp.erp_cloud.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {

    private final CityRepository cityRepository;

    /**
     * Retrieves all cities configured in the system.
     * @return List of CityResponseDTO containing static lookup data.
     */
    public List<CityResponseDTO> listAll() {
        return cityRepository.findAll().stream()
                .map(city -> CityResponseDTO.builder()
                        .id(city.getId())
                        .code(city.getCode())
                        .name(city.getName())
                        .build())
                .collect(Collectors.toList());
    }
}