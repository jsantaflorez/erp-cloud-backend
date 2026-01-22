package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.service.ThirdPartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        // We pass the Request DTO directly to the service
        ThirdParty saved = thirdPartyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Retrieves all third parties associated with the active company context.
     */
    @GetMapping
    public ResponseEntity<List<ThirdParty>> listAll() {
        return ResponseEntity.ok(thirdPartyService.listAll());
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Corrected variable name to match the one declared at the top
        thirdPartyService.delete(id);
        return ResponseEntity.noContent().build();
    }

}