package com.trackai.backend.service.impl;

import com.trackai.backend.config.ImageReferenceProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageReferencePreparationServiceImplTest {

    private ImageReferenceProperties properties;
    private ImageReferencePreparationServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new ImageReferenceProperties();
        properties.setMaxUploadBytes(8 * 1024 * 1024);
        properties.setMaxDimension(1024);
        properties.setMaxPixels(4_000_000);
        properties.setPdfDpi(96);
        service = new ImageReferencePreparationServiceImpl(properties);
    }

    @Test
    void keepsValidBoundedImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "reference.png", "image/png", png(320, 240));

        MultipartFile prepared = service.prepare(file);

        assertThat(prepared).isSameAs(file);
    }

    @Test
    void rendersFirstPdfPageAsBoundedPng() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(output);
        }
        MockMultipartFile file = new MockMultipartFile(
                "image", "portfolio.pdf", "application/pdf", output.toByteArray());

        MultipartFile prepared = service.prepare(file);

        assertThat(prepared.getContentType()).isEqualTo("image/png");
        assertThat(prepared.getOriginalFilename()).isEqualTo("portfolio-page-1.png");
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(prepared.getBytes()));
        assertThat(rendered).isNotNull();
        assertThat(Math.max(rendered.getWidth(), rendered.getHeight())).isLessThanOrEqualTo(1024);
    }

    @Test
    void rejectsInvalidImagePayload() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "broken.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.prepare(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void rejectsFileAboveConfiguredLimit() {
        properties.setMaxUploadBytes(1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "image", "large.pdf", "application/pdf", new byte[1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.prepare(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smaller than 1 MB");
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.WHITE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
