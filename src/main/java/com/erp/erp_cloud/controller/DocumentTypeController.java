package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.dto.DocumentTypeResponseDTO;
import com.erp.erp_cloud.service.DocumentTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-types")
@RequiredArgsConstructor
public class DocumentTypeController {

    private final DocumentTypeService documentTypeservice;


    @PostMapping
    public ResponseEntity<ApiResponse<DocumentTypeResponseDTO>> create(@Valid @RequestBody DocumentTypeRequest request) {
        DocumentTypeResponseDTO data = documentTypeservice.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Document type created successfully", true, data));
    }

    /**
     * Updates an existing document type, including the new default balancing account.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentTypeResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody DocumentTypeRequest request) {
        DocumentTypeResponseDTO data = documentTypeservice.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Document type updated successfully", true, data));
    }



    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentTypeResponseDTO>>> listAll() {
        List<DocumentTypeResponseDTO> data = documentTypeservice.listAll();
        return ResponseEntity.ok(new ApiResponse<>("Document types retrieved", true, data));
    }


    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentTypeResponseDTO>> getById(@PathVariable Long id) {
        DocumentTypeResponseDTO data = documentTypeservice.findByIdDto(id);
        return ResponseEntity.ok(new ApiResponse<>("Document type found", true, data));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DocumentTypeResponseDTO>>> search(@RequestParam String name) {
        List<DocumentTypeResponseDTO> data = documentTypeservice.searchByName(name);
        return ResponseEntity.ok(new ApiResponse<>("Search results retrieved", true, data));
    }

    /**
     * Soft-deactivates a document type so it can no longer be used for new entries.
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        documentTypeservice.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Document type deactivated successfully", true,null));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        documentTypeservice.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Document type activated successfully", true,null));
    }

    @PatchMapping("/{id}/reset-consecutive")
    public ResponseEntity<ApiResponse<Void>> resetConsecutive(
            @PathVariable Long id,
            @RequestParam Long newValue) {
        documentTypeservice.resetConsecutive(id, newValue);
        return ResponseEntity.ok(new ApiResponse<>("Consecutive reset successfully to " + newValue, true,null));
    }




}