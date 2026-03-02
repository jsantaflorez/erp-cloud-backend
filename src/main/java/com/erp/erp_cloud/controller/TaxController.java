package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.dto.TaxResponseDTO;
import com.erp.erp_cloud.service.TaxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/taxes")
@RequiredArgsConstructor
public class TaxController {

    private final TaxService taxService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaxResponseDTO>> create(@Valid @RequestBody TaxRequest request) {
        TaxResponseDTO data = taxService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Tax created successfully", true, data));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaxResponseDTO>>> listAll() {
        List<TaxResponseDTO> data = taxService.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Taxes retrieved successfully", true, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> findById(@PathVariable Long id) {
        TaxResponseDTO data = taxService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("Tax retrieved successfully", true, data));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaxResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody TaxRequest request) {
        TaxResponseDTO data = taxService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Tax updated successfully", true, data));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        taxService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Tax deactivated successfully", true, null));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        taxService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Tax activated successfully", true, null));
    }
}