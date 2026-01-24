package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.TaxRequest;
import com.erp.erp_cloud.entity.Tax;
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

    private final TaxService service;

    @PostMapping
    public ResponseEntity<Tax> create(@Valid @RequestBody TaxRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<List<Tax>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tax> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tax> update(
            @PathVariable Long id,
            @Valid @RequestBody TaxRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }
}