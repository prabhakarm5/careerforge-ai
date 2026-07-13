package com.trackai.backend.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.trackai.backend.config.GeminiResumeProperties;
import com.trackai.backend.exception.ResumeProcessingException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ResumeDocumentTextExtractor {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "png", "jpg", "jpeg", "webp");
    private final GeminiResumeProperties properties;

    public ExtractedResume extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResumeProcessingException(HttpStatus.BAD_REQUEST, "Please upload a resume file.");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw new ResumeProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "Resume file must be 5 MB or smaller.");
        }

        String safeName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(safeName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Supported resume formats are PDF, DOCX, TXT, PNG, JPG, and WEBP.");
        }

        try {
            byte[] bytes = file.getBytes();
            String text;
            String mimeType;
            byte[] inlineDocument = null;

            switch (extension) {
                case "pdf" -> {
                    validatePdfSignature(bytes);
                    text = extractPdf(bytes);
                    mimeType = "application/pdf";
                    // Native Gemini PDF understanding is used only for scanned or nearly empty PDFs.
                    if (text.length() < 300) {
                        inlineDocument = bytes;
                    }
                }
                case "docx" -> {
                    validateZipSignature(bytes);
                    text = extractDocx(bytes);
                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
                case "txt" -> {
                    text = new String(bytes, StandardCharsets.UTF_8);
                    mimeType = "text/plain";
                }
                case "png" -> {
                    validatePngSignature(bytes);
                    text = "";
                    mimeType = "image/png";
                    inlineDocument = bytes;
                }
                case "jpg", "jpeg" -> {
                    validateJpegSignature(bytes);
                    text = "";
                    mimeType = "image/jpeg";
                    inlineDocument = bytes;
                }
                case "webp" -> {
                    validateWebpSignature(bytes);
                    text = "";
                    mimeType = "image/webp";
                    inlineDocument = bytes;
                }
                default -> throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Supported resume formats are PDF, DOCX, TXT, PNG, JPG, and WEBP.");
            }

            text = normalize(text);
            if (text.isBlank() && inlineDocument == null) {
                throw new ResumeProcessingException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No readable resume text was found. Upload a text-based PDF, DOCX, or TXT file.");
            }
            if (text.length() > properties.getMaxResumeChars()) {
                text = text.substring(0, properties.getMaxResumeChars());
            }
            return new ExtractedResume(safeName, mimeType, text, inlineDocument);
        } catch (ResumeProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResumeProcessingException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The resume could not be read. It may be damaged or password protected.", ex);
        }
    }

    private String extractPdf(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        Set<String> links = new LinkedHashSet<>();
        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(bytes));
             PdfDocument pdf = new PdfDocument(reader)) {
            int pages = Math.min(pdf.getNumberOfPages(), 20);
            for (int page = 1; page <= pages; page++) {
                var pdfPage = pdf.getPage(page);
                text.append(PdfTextExtractor.getTextFromPage(pdfPage)).append('\n');
                pdfPage.getAnnotations().forEach(annotation -> {
                    var action = annotation.getPdfObject().getAsDictionary(PdfName.A);
                    var uri = action == null ? null : action.getAsString(PdfName.URI);
                    if (uri != null) addSafeLink(links, uri.toUnicodeString());
                });
            }
        }
        appendLinks(text, links);
        return text.toString();
    }

    private String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            StringBuilder text = new StringBuilder(extractor.getText());
            Set<String> links = new LinkedHashSet<>();
            document.getParagraphs().forEach(paragraph -> paragraph.getRuns().forEach(run -> {
                if (run instanceof XWPFHyperlinkRun hyperlinkRun) {
                    var hyperlink = hyperlinkRun.getHyperlink(document);
                    if (hyperlink != null) addSafeLink(links, hyperlink.getURL());
                }
            }));
            appendLinks(text, links);
            return text.toString();
        }
    }

    private void addSafeLink(Set<String> links, String value) {
        if (value == null) return;
        String link = value.trim();
        if (link.startsWith("https://") || link.startsWith("http://") || link.startsWith("mailto:")) {
            links.add(link);
        }
    }

    private void appendLinks(StringBuilder text, Set<String> links) {
        if (links.isEmpty()) return;
        text.append("\nSOURCE LINKS:\n");
        links.forEach(link -> text.append(link).append('\n'));
    }

    private void validatePdfSignature(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file is not a valid PDF.");
        }
    }

    private void validateZipSignature(byte[] bytes) {
        if (bytes.length < 2 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file is not a valid DOCX.");
        }
    }

    private void validatePngSignature(byte[] bytes) {
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P'
                || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The uploaded file is not a valid PNG image.");
        }
    }

    private void validateJpegSignature(byte[] bytes) {
        if (bytes.length < 3 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8
                || (bytes[2] & 0xFF) != 0xFF) {
            throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The uploaded file is not a valid JPEG image.");
        }
    }

    private void validateWebpSignature(byte[] bytes) {
        if (bytes.length < 12 || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F'
                || bytes[3] != 'F' || bytes[8] != 'W' || bytes[9] != 'E'
                || bytes[10] != 'B' || bytes[11] != 'P') {
            throw new ResumeProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The uploaded file is not a valid WEBP image.");
        }
    }
    private String normalize(String value) {
        if (value == null) return "";
        return value.replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    public String sanitizeFileName(String original) {
        String value = original == null ? "resume" : original;
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        value = value.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return value.isBlank() ? "resume" : value.substring(0, Math.min(180, value.length()));
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ExtractedResume(String fileName, String mimeType, String text, byte[] inlineDocument) {
    }
}
