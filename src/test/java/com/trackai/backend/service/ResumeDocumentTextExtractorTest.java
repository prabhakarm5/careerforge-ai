package com.trackai.backend.service;

import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.exception.ResumeProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumeDocumentTextExtractorTest {

    private ResumeDocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        GeminiResumeProperties properties = new GeminiResumeProperties();
        properties.setMaxFileBytes(1024);
        properties.setMaxResumeChars(100);
        extractor = new ResumeDocumentTextExtractor(properties);
    }

    @Test
    void extractsAndNormalizesTextResume() {
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "../Prabhakar Resume.txt",
                "text/plain",
                "Java developer\r\n\r\n\r\nSpring Boot".getBytes(StandardCharsets.UTF_8));

        ResumeDocumentTextExtractor.ExtractedResume result = extractor.extract(file);

        assertThat(result.fileName()).isEqualTo("Prabhakar Resume.txt");
        assertThat(result.mimeType()).isEqualTo("text/plain");
        assertThat(result.text()).isEqualTo("Java developer\n\nSpring Boot");
        assertThat(result.inlineDocument()).isNull();
    }

    @Test
    void acceptsPngResumeForGeminiVision() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        MockMultipartFile file = new MockMultipartFile(
                "resume", "resume.png", "image/png", png);

        ResumeDocumentTextExtractor.ExtractedResume result = extractor.extract(file);

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.text()).isEmpty();
        assertThat(result.inlineDocument()).containsExactly(png);
    }
    @Test
    void rejectsExtensionThatIsNotAllowListed() {
        MockMultipartFile file = new MockMultipartFile(
                "resume", "resume.exe", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(ResumeProcessingException.class)
                .hasMessageContaining("PDF, DOCX, TXT, PNG, JPG, and WEBP");
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "resume", "resume.txt", "text/plain", new byte[1025]);

        assertThatThrownBy(() -> extractor.extract(file))
                .isInstanceOf(ResumeProcessingException.class)
                .hasMessageContaining("5 MB or smaller");
    }
}
