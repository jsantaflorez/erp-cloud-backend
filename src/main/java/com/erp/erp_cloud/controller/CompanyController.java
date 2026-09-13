package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.CompanyResponseDTO;
import com.erp.erp_cloud.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Read-only access to the current tenant's own company data")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/me")
    @Operation(
            summary = "Get the current tenant's company",
            description = "Retrieves the company bound to the authenticated session's tenant context, " +
                    "including its configured chart-of-accounts template (if any)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Company retrieved successfully")
    public ResponseEntity<ApiResponse<CompanyResponseDTO>> getCurrentCompany() {
        CompanyResponseDTO data = companyService.getCurrentCompany();
        return ResponseEntity.ok(new ApiResponse<>("Company retrieved successfully", true, data));
    }
}
