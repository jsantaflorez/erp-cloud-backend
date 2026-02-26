package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/third-parties")
@RequiredArgsConstructor
public class ThirdPartyController {

    private final ThirdPartyService thirdPartyService;


    @PostMapping
    public ResponseEntity<ApiResponse<ThirdPartyResponseDTO>> create(@RequestBody ThirdPartyRequest request) {
        ThirdPartyResponseDTO created = thirdPartyService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("Third Party created successfully", true, created));
    }

    @GetMapping
    public ResponseEntity<Page<ThirdPartyResponseDTO>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "lastName") Pageable pageable) {
        return ResponseEntity.ok(thirdPartyService.listAll(search, pageable));
    }



    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ThirdPartyResponseDTO>> getById(@PathVariable Long id) {
        ThirdPartyResponseDTO data = thirdPartyService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>("Third party found", true, data));
    }


    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ThirdPartyResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody ThirdPartyRequest request) {
        ThirdPartyResponseDTO updated = thirdPartyService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>("Third party updated successfully", true, updated));
    }


    /**
     * Deactivate a Third Party (Logical delete).
     */

    @DeleteMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        thirdPartyService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>("Third Party deactivated successfully", true));
    }



    // ACTIVATE: Uses the helper constructor (data will be null/hidden)
    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        thirdPartyService.activate(id);
        return ResponseEntity.ok(new ApiResponse<>("Third Party activated successfully", true));
    }



}