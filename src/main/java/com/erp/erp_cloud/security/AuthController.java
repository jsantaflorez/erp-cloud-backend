package com.erp.erp_cloud.security;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.auth.AuthResponse;
import com.erp.erp_cloud.dto.auth.LoginRequest;
import com.erp.erp_cloud.dto.auth.PreAuthResponse;
import com.erp.erp_cloud.dto.auth.SwitchCompanyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public authentication endpoints -- B1 (auditoría F1-13, 2026-11-25):
 * login en dos pasos (see {@link TwoStepLoginService}).
 *
 * 1. POST /api/auth/login           { email, password }
 *    -> pre-session token (5 min) + the user's companies (name and NIT).
 * 2. POST /api/auth/select-company  { companyId }, Authorization: Bearer &lt;pre-session token&gt;
 *    -> the normal access token for that company.
 *
 * The old PUBLIC POST /api/auth/companies (company list for an e-mail,
 * without password) no longer exists: the companies are only listed after
 * the password has been verified. Both endpoints share the per-IP rate
 * limit (AuthRateLimitFilter) and failed passwords keep locking the account
 * (LoginAttemptService).
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TwoStepLoginService twoStepLoginService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<PreAuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        PreAuthResponse data = twoStepLoginService.start(loginRequest.email(), loginRequest.password());
        return ResponseEntity.ok(ApiResponse.success("Credentials verified. Choose a company.", data));
    }

    @PostMapping("/select-company")
    public ResponseEntity<ApiResponse<AuthResponse>> selectCompany(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SwitchCompanyRequest request) {
        AuthResponse data = twoStepLoginService.selectCompany(authorization, request.companyId());
        return ResponseEntity.ok(ApiResponse.success("Authentication successful.", data));
    }
}
