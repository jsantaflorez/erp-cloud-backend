package com.erp.erp_cloud.service.certificates;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Tiny shared helper (2026-11-07) -- both LegalDocumentTemplateService
 * (built-in default template text) and WithholdingCertificateService
 * (the code-controlled document skeleton) need to read a plain UTF-8
 * classpath resource into a String. Not worth a Spring bean for two
 * call sites.
 */
final class ClasspathTemplateLoader {

    private ClasspathTemplateLoader() {
    }

    static String readUtf8(String classpathPath) {
        try (InputStream in = new ClassPathResource(classpathPath).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read classpath template: " + classpathPath, e);
        }
    }
}
