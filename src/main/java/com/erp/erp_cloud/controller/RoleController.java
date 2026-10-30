package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.RoleOptionDTO;
import com.erp.erp_cloud.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Roles selectable for the current company's user-management screen")
public class RoleController {

    private final UserManagementService userManagementService;

    @GetMapping
    @Operation(summary = "List roles available for the current company", description = "Company-specific roles plus system-wide ones (company = null).")
    public ResponseEntity<ApiResponse<List<RoleOptionDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", userManagementService.listAvailableRoles()));
    }
}
