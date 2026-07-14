package com.trackai.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackai.backend.entity.CoverLetterProject;
import com.trackai.backend.entity.ResumeProject;
import com.trackai.backend.enums.CoverLetterStyle;
import com.trackai.backend.service.impl.CoverLetterDocumentServiceImpl;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CoverLetterDocumentServiceTest {

    private final CoverLetterDocumentService service =
            new CoverLetterDocumentServiceImpl(new ObjectMapper());

    @Test
    void createsValidPdfWithLetterContent() {
        byte[] bytes = service.createPdf(project(), resume());

        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(bytes.length).isGreaterThan(1_000);
    }

    @Test
    void createsReadableDocxWithLetterContent() throws Exception {
        byte[] bytes = service.createDocx(project(), resume());

        assertThat(new String(bytes, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("Prabhakar Mishra", "Backend Engineer", "Dear Hiring Manager");
        }
    }

    private CoverLetterProject project() {
        return CoverLetterProject.builder()
                .company("CareerForge")
                .role("Backend Engineer")
                .style(CoverLetterStyle.PROFESSIONAL)
                .content("Dear Hiring Manager,\n\nI am applying for the Backend Engineer role.\n\nSincerely,\nPrabhakar Mishra")
                .build();
    }

    private ResumeProject resume() {
        return ResumeProject.builder()
                .analysisJson("""
                        {"candidate":{"name":"Prabhakar Mishra","email":"test@example.com",
                        "phone":"+91 9999999999","location":"India"}}
                        """)
                .build();
    }
}
