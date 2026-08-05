package com.trackai.backend.service.impl;

import com.trackai.backend.config.ImageReferenceProperties;
import com.trackai.backend.service.ImageReferencePreparationService;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ImageReferencePreparationServiceImpl implements ImageReferencePreparationService {

    private final ImageReferenceProperties properties;

    @Override
    public MultipartFile prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) return file;
        validateSize(file);

        String contentType = normalizedContentType(file);
        if ("application/pdf".equals(contentType)) return renderPdf(file);
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Reference file must be PNG, JPG, WebP, or PDF.");
        }
        return normalizeImage(file);
    }

    private void validateSize(MultipartFile file) {
        long maxBytes = Math.max(1024 * 1024, properties.getMaxUploadBytes());
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Reference file must be smaller than " + megabytes(maxBytes) + " MB.");
        }
    }

    private MultipartFile renderPdf(MultipartFile file) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("careerforge-image-reference-", ".pdf");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (PDDocument document = Loader.loadPDF(temporaryFile.toFile())) {
                if (document.getNumberOfPages() == 0) {
                    throw new IllegalArgumentException("The uploaded PDF has no pages.");
                }
                var box = document.getPage(0).getCropBox();
                float requestedDpi = Math.max(72f, properties.getPdfDpi());
                float projectedWidth = box.getWidth() * requestedDpi / 72f;
                float projectedHeight = box.getHeight() * requestedDpi / 72f;
                float largest = Math.max(projectedWidth, projectedHeight);
                float boundedDpi = largest > maxDimension()
                        ? requestedDpi * maxDimension() / largest
                        : requestedDpi;

                BufferedImage rendered = new PDFRenderer(document)
                        .renderImageWithDPI(0, Math.max(72f, boundedDpi), ImageType.RGB);
                return asPng(rendered, pdfImageName(file.getOriginalFilename()));
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "The PDF could not be read. Upload a valid, non-encrypted PDF.", error);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // The operating system can clean up a locked temporary file later.
                }
            }
        }
    }

    private MultipartFile normalizeImage(MultipartFile file) {
        try {
            ImageDimensions dimensions = readDimensions(file);
            long pixels = (long) dimensions.width() * dimensions.height();
            if (pixels > Math.max(1_000_000L, properties.getMaxPixels())) {
                throw new IllegalArgumentException("Reference image dimensions are too large.");
            }
            if (dimensions.width() <= maxDimension() && dimensions.height() <= maxDimension()) {
                return file;
            }

            BufferedImage source = ImageIO.read(file.getInputStream());
            if (source == null) throw new IllegalArgumentException("Reference image is invalid or corrupted.");
            return asPng(resize(source, maxDimension()), imageName(file.getOriginalFilename()));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (IOException error) {
            throw new IllegalArgumentException("Reference image could not be read.", error);
        }
    }

    private ImageDimensions readDimensions(MultipartFile file) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.getInputStream())) {
            if (stream == null) throw new IllegalArgumentException("Reference image is invalid or corrupted.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Reference image is invalid or unsupported.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage resize(BufferedImage source, int maxDimension) {
        double scale = Math.min((double) maxDimension / source.getWidth(),
                (double) maxDimension / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private MultipartFile asPng(BufferedImage image, String fileName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalArgumentException("Reference preview could not be created.");
        }
        return new InMemoryMultipartFile("image", fileName, "image/png", output.toByteArray());
    }

    private String normalizedContentType(MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(type) || name.endsWith(".pdf")) return "application/pdf";
        return type;
    }

    private int maxDimension() {
        return Math.max(512, properties.getMaxDimension());
    }

    private long megabytes(long bytes) {
        return Math.max(1, bytes / (1024 * 1024));
    }

    private String pdfImageName(String originalName) {
        String base = originalName == null || originalName.isBlank()
                ? "reference" : originalName.replaceFirst("(?i)\\.pdf$", "");
        return base + "-page-1.png";
    }

    private String imageName(String originalName) {
        String base = originalName == null || originalName.isBlank()
                ? "reference" : originalName.replaceFirst("(?i)\\.[a-z0-9]+$", "");
        return base + "-optimized.png";
    }

    private record ImageDimensions(int width, int height) {}

    private static final class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes.clone();
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File destination) throws IOException {
            Files.write(destination.toPath(), bytes);
        }
    }
}
