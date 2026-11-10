package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateRequest;
import com.erp.erp_cloud.dto.certificates.LegalDocumentTemplateResponseDTO;
import com.erp.erp_cloud.service.certificates.LegalDocumentTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only CRUD for a company's own editable legal-document
 * templates (2026-11-07) -- see
 * claude/propuesta-certificados-retencion-2026-11-07.md. Only the
 * accountant/admin-tier edits these; generating an actual certificate
 * from one is WithholdingCertificateController's job.
 */
@RestController
@RequestMapping("/api/v1/legal-document-templates")
@RequiredArgsConstructor
@Tag(name = "Legal Document Templates", description = "Per-company editable text templates for legal documents (withholding certificates, etc.)")
@PreAuthorize("@authz.isAdmin()")
public class LegalDocumentTemplateController {

    private final LegalDocumentTemplateService templateService;

    @GetMapping
    @Operation(summary = "List configured templates", description = "Lists every legal-document template this company has saved (does not include unsaved defaults).")
    public ResponseEntity<ApiResponse<List<LegalDocumentTemplateResponseDTO>>> list() {
        List<LegalDocumentTemplateResponseDTO> data = templateService.list();
        return ResponseEntity.ok(new ApiResponse<>("Templates retrieved successfully", true, data));
    }

    @GetMapping("/{templateKey}")
    @Operation(summary = "Get a template (or its built-in default)", description = "Returns the company's saved template for this key, or the built-in default if none has been saved yet.")
    public ResponseEntity<ApiResponse<LegalDocumentTemplateResponseDTO>> get(@PathVariable String templateKey) {
        LegalDocumentTemplateResponseDTO data = templateService.getOrDefault(templateKey);
        return ResponseEntity.ok(new ApiResponse<>("Template retrieved successfully", true, data));
    }

    @GetMapping("/{templateKey}/default")
    @Operation(summary = "Get the built-in default (ignoring any saved override)", description = "Used by the admin screen's \"Restaurar plantilla por defecto\" action -- returns the hardcoded fallback text regardless of what the company has saved, so the admin can preview/apply it before saving.")
    public ResponseEntity<ApiResponse<LegalDocumentTemplateResponseDTO>> getDefault(@PathVariable String templateKey) {
        LegalDocumentTemplateResponseDTO data = templateService.builtInDefault(templateKey);
        return ResponseEntity.ok(new ApiResponse<>("Default template retrieved successfully", true, data));
    }

    @PutMapping("/{templateKey}")
    @Operation(summary = "Save a template", description = "Creates or updates (upsert) this company's own copy of the template.")
    public ResponseEntity<ApiResponse<LegalDocumentTemplateResponseDTO>> save(
            @PathVariable String templateKey,
            @Valid @RequestBody LegalDocumentTemplateRequest request) {
        LegalDocumentTemplateResponseDTO data = templateService.upsert(templateKey, request);
        return ResponseEntity.ok(new ApiResponse<>("Template saved successfully", true, data));
    }
}
