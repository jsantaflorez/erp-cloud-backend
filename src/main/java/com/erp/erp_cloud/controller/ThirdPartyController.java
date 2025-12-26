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
@RequestMapping("/api/terceros")
@RequiredArgsConstructor
public class ThirdPartyController {

    private final ThirdPartyService terceroService;

    @PostMapping
    public ResponseEntity<ThirdParty> createTercero(
            @Valid @RequestBody ThirdPartyRequest request
    ) {
        ThirdParty tercero = terceroService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tercero);
    }
    @GetMapping
    public ResponseEntity<List<ThirdParty>> getAllTerceros() {
        return ResponseEntity.ok(terceroService.listAll());
    }

    @GetMapping("/documento/{numeroDocumento}")
    public ResponseEntity<ThirdParty> getByNumeroDocumento(
            @PathVariable String numeroDocumento
    ) {
        ThirdParty tercero = terceroService.getByNumeroDocumento(numeroDocumento);
        return ResponseEntity.ok(tercero);
    }

}
