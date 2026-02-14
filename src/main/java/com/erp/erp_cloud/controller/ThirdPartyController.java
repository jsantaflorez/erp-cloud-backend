package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.dto.ThirdPartyResponseDTO;

import com.erp.erp_cloud.service.ThirdPartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/third-parties")
@RequiredArgsConstructor
public class ThirdPartyController {

    private final ThirdPartyService thirdPartyService;

    @PostMapping
    public ResponseEntity<ThirdPartyResponseDTO> create(@Valid @RequestBody ThirdPartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(thirdPartyService.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<ThirdPartyResponseDTO>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(thirdPartyService.listAll(search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThirdPartyResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(thirdPartyService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThirdPartyResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody ThirdPartyRequest request) {
        return ResponseEntity.ok(thirdPartyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Triggering the deactivation logic
        thirdPartyService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}