package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.entity.ThirdParty;
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

    /**
     * Creates a new Third Party.
     * The mapping from Request DTO to Entity is handled within the Service layer.
     */
    @PostMapping
    public ResponseEntity<ThirdParty> create(@Valid @RequestBody ThirdPartyRequest request) {
        // Pass the Request DTO directly to the service
        ThirdParty saved = thirdPartyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Retrieves a paginated list of third parties, optionally filtered by a search term.
     * The search term covers names, business names, and document numbers.
     */
    @GetMapping
    public ResponseEntity<Page<ThirdParty>> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "lastName") Pageable pageable) {
        // Pass both the search term and pagination settings to the service
        return ResponseEntity.ok(thirdPartyService.listAll(search, pageable));
    }

    /**
     * Finds a specific third party by its internal database ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ThirdParty> getById(@PathVariable Long id) {
        return ResponseEntity.ok(thirdPartyService.findById(id));
    }

    /**
     * Finds a third party by its document number (e.g., NIT, RUT, DNI).
     */
    @GetMapping("/document/{documentNumber}")
    public ResponseEntity<ThirdParty> getByDocumentNumber(@PathVariable String documentNumber) {
        return ResponseEntity.ok(thirdPartyService.getByDocumentNumber(documentNumber));
    }

    /**
     * Updates an existing third party's information.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ThirdParty> update(
            @PathVariable Long id,
            @Valid @RequestBody ThirdPartyRequest request) {
        ThirdParty updated = thirdPartyService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a third party record by its ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        thirdPartyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}