package com.erp.erp_cloud.controller;

import com.erp.erp_cloud.dto.ApiResponse;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateData;
import com.erp.erp_cloud.dto.certificates.WithholdingCertificateParametersDTO;
import com.erp.erp_cloud.service.certificates.WithholdingCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Withholding Certificate generation (2026-11-07) -- see
 * claude/propuesta-certificados-retencion-2026-11-07.md. /preview and
 * /pdf both call WithholdingCertificateService.renderHtml() for the
 * exact same HTML, so the preview the accountant checks on screen is
 * byte-for-byte what gets printed into the PDF.
 */
@RestController
@RequestMapping("/api/v1/withholding-certificates")
@RequiredArgsConstructor
@Tag(name = "Withholding Certificates", description = "Generates the Certificado de Retencion PDF for a third party/tax year/tax type")
public class WithholdingCertificateController {

    private final WithholdingCertificateService certificateService;

    @GetMapping("/parameters")
    @Operation(summary = "Get screen parameters", description = "Available retention tax types, and (only when taxType is given) third parties with retention movements in that tax year.")
    public ResponseEntity<ApiResponse<WithholdingCertificateParametersDTO>> getParameters(
            @RequestParam int taxYear,
            @RequestParam(required = false) String taxType) {
        WithholdingCertificateParametersDTO data = certificateService.getParameters(taxYear, taxType);
        return ResponseEntity.ok(new ApiResponse<>("Parameters retrieved successfully", true, data));
    }

    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Preview the certificate as HTML", description = "Returns the exact HTML that would be rendered into the PDF, for an inline preview.")
    public ResponseEntity<String> preview(
            @RequestParam int taxYear,
            @RequestParam String taxType,
            @RequestParam String thirdPartyDocument) {
        WithholdingCertificateData data = certificateService.getCertificateData(taxYear, taxType, thirdPartyDocument);
        String html = certificateService.renderHtml(data);
        return ResponseEntity.ok(html);
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the certificate as PDF", description = "Generates and downloads the Certificado de Retencion for this third party/tax year/tax type.")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam int taxYear,
            @RequestParam String taxType,
            @RequestParam String thirdPartyDocument) {
        WithholdingCertificateData data = certificateService.getCertificateData(taxYear, taxType, thirdPartyDocument);
        String html = certificateService.renderHtml(data);
        byte[] pdf = certificateService.renderPdf(html);

        String filename = "certificado-retencion-" + sanitize(thirdPartyDocument) + "-"
                + sanitize(taxType) + "-" + taxYear + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/zip")
    @Operation(summary = "Download several certificates as a single ZIP",
            description = "Bulk alternative to /pdf: generates one PDF per selected third party and streams "
                    + "them into a single .zip as each one finishes, so the browser starts downloading "
                    + "immediately and a large batch never has to sit fully in server memory first. "
                    + "See claude/analisis-generacion-masiva-certificados-retencion-2026-11-09.md.")
    public void downloadZip(
            @RequestParam int taxYear,
            @RequestParam String taxType,
            // required = false: an absent/malformed param should still
            // reach resolveZipDocuments()'s own THIRD_PARTY_REQUIRED check
            // (which already treats null the same as empty), rather than
            // bubbling up as a raw 500 from a missing-parameter exception
            // that has no dedicated handler in GlobalExceptionHandler.
            @RequestParam(required = false) List<String> thirdPartyDocuments,
            HttpServletResponse response) throws IOException {
        List<String> documents = certificateService.resolveZipDocuments(taxYear, taxType, thirdPartyDocuments);

        String filename = "certificados-retencion-" + sanitize(taxType) + "-" + taxYear + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        certificateService.writeZip(taxYear, taxType, documents, response.getOutputStream());
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9-]", "");
    }
}
