package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.ChartAccountTemplateEntryDTO;
import com.erp.erp_cloud.enums.ChartTemplateType;
import com.erp.erp_cloud.service.ChartAccountTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only lookup of reference chart-of-accounts templates (PUC Comercial,
 * PUC Solidario, ...), used by the frontend to suggest an account name
 * while the user types a code in the "Crear Cuenta" form. Not tenant-aware
 * by design -- this is shared reference data, not company data.
 */
@RestController
@RequestMapping("/api/v1/chart-account-templates")
@Tag(name = "Chart Account Templates", description = "Reference PUC templates (Comercial, Solidario) used for account-name suggestions")
@RequiredArgsConstructor
public class ChartAccountTemplateController {

    private final ChartAccountTemplateService service;

    @GetMapping
    @Operation(
            summary = "List entries of one chart-of-accounts template",
            description = "Retrieves every code/name entry of the given template (COMERCIAL or SOLIDARIO), " +
                    "for the frontend to do its own prefix-based lookup while the user types an account code."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Template entries retrieved successfully")
    public ResponseEntity<ApiResponse<List<ChartAccountTemplateEntryDTO>>> list(
            @RequestParam ChartTemplateType type) {
        List<ChartAccountTemplateEntryDTO> data = service.getEntries(type);
        return ResponseEntity.ok(new ApiResponse<>("Chart account template entries retrieved successfully", true, data));
    }
}
