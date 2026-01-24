package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.DocumentTypeRequest;
import com.erp.erp_cloud.entity.DocumentType;
import com.erp.erp_cloud.service.DocumentTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
@RequiredArgsConstructor
public class DocumentTypeController {

    private final DocumentTypeService service;

    @PostMapping
    public ResponseEntity<DocumentType> create(@Valid @RequestBody DocumentTypeRequest request) {
        // Convert Request DTO to Entity (can be moved to Service later if preferred)
        DocumentType entity = new DocumentType();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setPrefix(request.getPrefix());
        entity.setAccounting(request.getIsAccounting());
        entity.setLegalResolution(request.getLegalResolution());

        if (request.getCurrentConsecutive() != null) {
            entity.setCurrentConsecutive(request.getCurrentConsecutive());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(entity));
    }

    @GetMapping
    public ResponseEntity<List<DocumentType>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentType> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<DocumentType>> search(@RequestParam String name) {
        return ResponseEntity.ok(service.searchByName(name));
    }

    @PatchMapping("/{id}/reset-consecutive")
    public ResponseEntity<Void> resetConsecutive(
            @PathVariable Long id,
            @RequestParam Long newValue) {
        service.resetConsecutive(id, newValue);
        return ResponseEntity.noContent().build();
    }
}