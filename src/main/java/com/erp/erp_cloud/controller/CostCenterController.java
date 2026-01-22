package com.erp.erp_cloud.controller;


import com.erp.erp_cloud.dto.CostCenterRequest;
import com.erp.erp_cloud.entity.CostCenter;
import com.erp.erp_cloud.service.CostCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/cost-centers")
@RequiredArgsConstructor
public class CostCenterController {

    private final CostCenterService service;

    @GetMapping
    public ResponseEntity<List<CostCenter>> getAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/roots")
    public ResponseEntity<List<CostCenter>> getRoots() {
        return ResponseEntity.ok(service.getRoots());
    }

    @GetMapping("/movement")
    public ResponseEntity<List<CostCenter>> getMovementAccounts() {
        return ResponseEntity.ok(service.getMovementAccounts());
    }

    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<CostCenter>> getChildren(@PathVariable Long parentId) {
        return ResponseEntity.ok(service.getChildren(parentId));
    }

    @PostMapping
    public ResponseEntity<CostCenter> create(@RequestBody CostCenterRequest request) {
        return new ResponseEntity<>(service.create(request), HttpStatus.CREATED);

    }


    // Update an existing cost center
    @PutMapping("/{id}")
    public ResponseEntity<CostCenter> update(@PathVariable Long id, @RequestBody CostCenterRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Calling the service method
        service.delete(id);
        return ResponseEntity.noContent().build(); // 204 No Content for successful deletions
    }

}