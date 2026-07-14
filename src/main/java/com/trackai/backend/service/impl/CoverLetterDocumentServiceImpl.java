package com.trackai.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.trackai.backend.entity.CoverLetterProject;
import com.trackai.backend.entity.ResumeProject;
import com.trackai.backend.exception.CoverLetterException;
import com.trackai.backend.service.CoverLetterDocumentService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoverLetterDocumentServiceImpl implements CoverLetterDocumentService {

    private static final DeviceRgb INK = new DeviceRgb(20, 33, 48);
    private static final DeviceRgb ACCENT = new DeviceRgb(8, 145, 178);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final ObjectMapper objectMapper;

    @Override
    public byte[] createPdf(CoverLetterProject project, ResumeProject resumeProject) {
        Candidate candidate = candidate(resumeProject);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            try (PdfWriter writer = new PdfWriter(output);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                document.setMargins(54, 62, 48, 62);
                pdf.getDocumentInfo()
                        .setTitle(project.getRole() + " Cover Letter")
                        .setAuthor(candidate.name())
                        .setSubject("Cover letter for " + project.getCompany());

                document.add(new Paragraph(candidate.name())
                        .setFont(bold).setFontSize(19).setFontColor(INK)
                        .setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));
                if (!candidate.contactLine().isBlank()) {
                    document.add(new Paragraph(candidate.contactLine())
                            .setFont(regular).setFontSize(9).setFontColor(ACCENT)
                            .setTextAlignment(TextAlignment.CENTER).setMarginTop(0).setMarginBottom(8));
                }
                document.add(new Paragraph(" ")
                        .setBorderBottom(new SolidBorder(ACCENT, 1))
                        .setMarginTop(0).setMarginBottom(22));

                document.add(new Paragraph(LocalDate.now().format(DATE_FORMAT))
                        .setFont(regular).setFontSize(10.5f).setFontColor(INK).setMarginBottom(14));
                document.add(new Paragraph("Hiring Team\n" + project.getCompany())
                        .setFont(regular).setFontSize(10.5f).setFontColor(INK)
                        .setMultipliedLeading(1.25f).setMarginBottom(14));
                document.add(new Paragraph("Re: " + project.getRole())
                        .setFont(bold).setFontSize(10.5f).setFontColor(INK).setMarginBottom(18));

                for (String block : contentBlocks(project.getContent())) {
                    document.add(new Paragraph(block)
                            .setFont(regular).setFontSize(10.6f).setFontColor(INK)
                            .setMultipliedLeading(1.42f).setMarginTop(0).setMarginBottom(11));
                }
            }
            return output.toByteArray();
        } catch (Exception ex) {
            throw new CoverLetterException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The cover letter PDF could not be generated.", ex);
        }
    }

    @Override
    public byte[] createDocx(CoverLetterProject project, ResumeProject resumeProject) {
        Candidate candidate = candidate(resumeProject);
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            setMargins(document, 900);

            XWPFParagraph name = document.createParagraph();
            name.setAlignment(ParagraphAlignment.CENTER);
            addRun(name, candidate.name(), true, 16, "173044");

            if (!candidate.contactLine().isBlank()) {
                XWPFParagraph contact = document.createParagraph();
                contact.setAlignment(ParagraphAlignment.CENTER);
                contact.setSpacingAfter(240);
                addRun(contact, candidate.contactLine(), false, 9, "0891B2");
            }

            paragraph(document, LocalDate.now().format(DATE_FORMAT), false, 10, 160);
            paragraph(document, "Hiring Team\n" + project.getCompany(), false, 10, 160);
            paragraph(document, "Re: " + project.getRole(), true, 10, 220);

            for (String block : contentBlocks(project.getContent())) {
                XWPFParagraph body = document.createParagraph();
                body.setSpacingAfter(170);
                body.setSpacingBetween(1.15);
                addRun(body, block, false, 10, "142130");
            }

            document.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new CoverLetterException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The cover letter DOCX could not be generated.", ex);
        }
    }

    private void paragraph(XWPFDocument document, String text, boolean bold, int size, int after) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(after);
        addRun(paragraph, text, bold, size, "142130");
    }

    private void addRun(XWPFParagraph paragraph, String text, boolean bold, int size, String color) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily("Arial");
        run.setFontSize(size);
        run.setColor(color);
    }

    private void setMargins(XWPFDocument document, int twips) {
        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        BigInteger value = BigInteger.valueOf(twips);
        margins.setTop(value);
        margins.setBottom(value);
        margins.setLeft(value);
        margins.setRight(value);
    }

    private Candidate candidate(ResumeProject project) {
        try {
            JsonNode root = project.getGeneratedResumeJson() == null || project.getGeneratedResumeJson().isBlank()
                    ? objectMapper.readTree(project.getAnalysisJson()).path("candidate")
                    : objectMapper.readTree(project.getGeneratedResumeJson());
            JsonNode contact = root.path("contact").isObject() ? root.path("contact") : root;
            String name = text(root, "name", "Candidate");
            String line = join(" | ",
                    text(contact, "email", ""), text(contact, "phone", ""),
                    text(contact, "location", ""), text(contact, "linkedin", ""),
                    text(contact, "portfolio", ""));
            return new Candidate(name, line);
        } catch (Exception ignored) {
            return new Candidate("Candidate", "");
        }
    }

    private List<String> contentBlocks(String value) {
        List<String> blocks = new ArrayList<>();
        for (String block : value.trim().split("(?:\\r?\\n){2,}")) {
            String normalized = block.trim();
            if (!normalized.isBlank()) blocks.add(normalized);
        }
        return blocks.isEmpty() ? List.of(value.trim()) : blocks;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private String join(String delimiter, String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) present.add(value.trim());
        return String.join(delimiter, present);
    }

    private record Candidate(String name, String contactLine) {}
}
