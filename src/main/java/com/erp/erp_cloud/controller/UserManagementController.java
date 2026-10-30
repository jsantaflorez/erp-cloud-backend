package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.ChangePasswordRequest;
import com.erp.erp_cloud.dto.CreateUserRequest;
import com.erp.erp_cloud.dto.UpdateUserRequest;
import com.erp.erp_cloud.dto.UserLookupResultDTO;
import com.erp.erp_cloud.dto.UserSummaryDTO;
import com.erp.erp_cloud.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Tenant-scoped user administration: who has access to the current company and with which roles")
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    @Operation(summary = "List users of the current company", description = "Every user with at least one role in the current tenant, with only their roles in THIS company.")
    public ResponseEntity<ApiResponse<List<UserSummaryDTO>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", userManagementService.listUsers()));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Check if an email is already a registered user", description = "Used by the 'add user' form to decide whether to ask for a new password or just grant access.")
    public ResponseEntity<ApiResponse<UserLookupResultDTO>> lookup(@RequestParam String email) {
        return ResponseEntity.ok(ApiResponse.success("Lookup completed", userManagementService.lookupByEmail(email)));
    }

    @PostMapping
    @Operation(summary = "Create or invite a user into the current company")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> create(@Valid @RequestBody CreateUserRequest request) {
        UserSummaryDTO data = userManagementService.createOrInviteUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User saved successfully", data));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a user's name")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", userManagementService.updateUser(id, request)));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle a user's GLOBAL active flag", description = "Affects login across EVERY company this user has access to, not just the current one.")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("User status updated", userManagementService.toggleActive(id)));
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Reset a user's password", description = "Password is global to the user, not per-company.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@PathVariable Long id, @Valid @RequestBody ChangePasswordRequest request) {
        userManagementService.changePassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));
    }

    @PostMapping("/{id}/roles/{roleId}")
    @Operation(summary = "Grant a role, in the current company, to a user")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> assignRole(@PathVariable Long id, @PathVariable Long roleId) {
        return ResponseEntity.ok(ApiResponse.success("Role assigned successfully", userManagementService.assignRole(id, roleId)));
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @Operation(summary = "Revoke a role, in the current company, from a user")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> removeRole(@PathVariable Long id, @PathVariable Long roleId) {
        return ResponseEntity.ok(ApiResponse.success("Role removed successfully", userManagementService.removeRole(id, roleId)));
    }

    @DeleteMapping("/{id}/company-access")
    @Operation(summary = "Remove a user from the current company entirely", description = "Deletes every role this user has in the current company. Does not touch their access to any other company.")
    public ResponseEntity<ApiResponse<Void>> revokeCompanyAccess(@PathVariable Long id) {
        userManagementService.revokeCompanyAccess(id);
        return ResponseEntity.ok(ApiResponse.success("User removed from this company", null));
    }
}
