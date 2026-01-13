package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.entity.City;
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
    public ResponseEntity<ThirdParty> create(@Valid @RequestBody ThirdPartyRequest request) {
        // 1. Convertimos el DTO a la Entidad
        ThirdParty thirdParty = new ThirdParty();

        // Mapeo de campos básicos
        thirdParty.setDocumentNumber(request.getDocumentNumber());
        thirdParty.setDocumentType(request.getDocumentType());
        thirdParty.setVerificationDigit(request.getVerificationDigit());
        thirdParty.setPersonType(request.getPersonType());
        thirdParty.setTaxRegime(request.getTaxRegime());
        thirdParty.setFirstName(request.getFirstName());
        thirdParty.setMiddleName(request.getMiddleName());
        thirdParty.setLastName(request.getLastName());
        thirdParty.setSecondLastName(request.getSecondLastName());
        thirdParty.setBusinessName(request.getBusinessName());
        thirdParty.setEmail(request.getEmail());
        thirdParty.setMobile(request.getMobile());
        thirdParty.setPhone(request.getPhone());
        thirdParty.setAddress(request.getAddress());

        // 2. Cargamos la relación de la Ciudad (evita el error de city_id null)
        City city = new City();
        city.setId(request.getCityId());
        thirdParty.setCity(city);

        // 3. El servicio se encarga de la Company y el guardado
        ThirdParty saved = thirdPartyService.create(thirdParty);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }


    @GetMapping
    public ResponseEntity<List<ThirdParty>> listAll() {
        return ResponseEntity.ok(thirdPartyService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThirdParty> getById(@PathVariable Long id) {
        return ResponseEntity.ok(thirdPartyService.findById(id));
    }

    @GetMapping("/document/{documentNumber}")
    public ResponseEntity<ThirdParty> getByDocumentNumber(
            @PathVariable String documentNumber
    ) {
        return ResponseEntity.ok(
                thirdPartyService.getByDocumentNumber(documentNumber)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThirdParty> update(@PathVariable Long id, @Valid @RequestBody ThirdPartyRequest request) {
        ThirdParty updated = thirdPartyService.update(id, request);
        return ResponseEntity.ok(updated);
    }

}
