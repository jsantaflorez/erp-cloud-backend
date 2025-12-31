package com.erp.erp_cloud.controller;

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

    @PostMapping
    public ResponseEntity<ThirdParty> create(
            @Valid @RequestBody ThirdParty thirdParty
    ) {
        ThirdParty created = thirdPartyService.create(thirdParty);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ThirdParty>> listAll() {
        return ResponseEntity.ok(thirdPartyService.listAll());
    }

    @GetMapping("/document/{documentNumber}")
    public ResponseEntity<ThirdParty> getByDocumentNumber(
            @PathVariable String documentNumber
    ) {
        return ResponseEntity.ok(
                thirdPartyService.getByDocumentNumber(documentNumber)
        );
    }
}
