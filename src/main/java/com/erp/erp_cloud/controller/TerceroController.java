package com.erp.erp_cloud.controller;


import com.erp.erp_cloud.dto.TerceroRequest;
import com.erp.erp_cloud.entity.Tercero;
import com.erp.erp_cloud.service.TerceroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/terceros")
@RequiredArgsConstructor
public class TerceroController {

    private final TerceroService terceroService;

    @PostMapping
    public ResponseEntity<Tercero> createTercero(
            @Valid @RequestBody TerceroRequest request
    ) {
        Tercero tercero = terceroService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tercero);
    }
    @GetMapping
    public ResponseEntity<List<Tercero>> getAllTerceros() {
        return ResponseEntity.ok(terceroService.listAll());
    }

    @GetMapping("/documento/{numeroDocumento}")
    public ResponseEntity<Tercero> getByNumeroDocumento(
            @PathVariable String numeroDocumento
    ) {
        Tercero tercero = terceroService.getByNumeroDocumento(numeroDocumento);
        return ResponseEntity.ok(tercero);
    }

}
