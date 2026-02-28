package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.dto.TaxResponseDTO;
import com.erp.erp_cloud.entity.Tax;
import com.erp.erp_cloud.service.TaxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/v1/taxes")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService service;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxResponseDTO>> create(@Valid @RequestBody TaxRequest request) {
        TaxResponseDTO data = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Tax rule created successfully", true, data));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxResponseDTO>>> listAll() {
        List<TaxResponseDTO> data = service.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Taxes retrieved successfully", true, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> getById(@PathVariable Long id) {
        TaxResponseDTO data = service.mapToResponseDTO(service.findEntityById(id));
        return ResponseEntity.ok(new ApiResponse<>("Tax found", true, data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody TaxRequest request) {
        TaxResponseDTO data = service.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Tax updated successfully", true, data));
    }
}