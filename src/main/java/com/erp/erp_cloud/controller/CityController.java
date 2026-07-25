package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.CityResponseDTO;
import com.erp.erp_cloud.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cities")
@Tag(name = "Cities", description = "Endpoints for retrieving master city configurations and geographic lookup data")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    @GetMapping
    @Operation(summary = "List all cities", description = "Retrieves a comprehensive list of all cities available for geographic referencing.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of cities retrieved successfully")
    public ResponseEntity<ApiResponse<List<CityResponseDTO>>> listAll() {
        List<CityResponseDTO> data = cityService.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Cities retrieved successfully", true, data));
    }
}