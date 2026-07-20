package com.trackai.backend.service.impl;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.trackai.backend.entity.PaymentTransaction;
import com.trackai.backend.entity.SubscriptionPlan;
import com.trackai.backend.entity.User;
import com.trackai.backend.service.PdfInvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PdfInvoiceServiceImpl implements PdfInvoiceService {
    private static final DeviceRgb BRAND = new DeviceRgb(79, 70, 229);
    private static final DeviceRgb MUTED = new DeviceRgb(71, 85, 105);
    private static final DeviceRgb ROW = new DeviceRgb(248, 250, 252);

    @Override
    public byte[] generateInvoice(User user, PaymentTransaction txn, SubscriptionPlan plan,
            boolean success, String failureReason) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
            Document document = new Document(pdfDoc);
            document.setMargins(42, 42, 42, 42);

            document.add(new Paragraph("CareerForge AI")
                    .setBold().setFontSize(24).setFontColor(BRAND));
            document.add(new Paragraph(success ? "Verified payment receipt" : "Payment status notice")
                    .setFontSize(13).setFontColor(success ? new DeviceRgb(22, 163, 74) : ColorConstants.RED));
            document.add(new Paragraph("A secure record for your CareerForge AI wallet transaction.")
                    .setFontSize(9).setFontColor(MUTED).setMarginBottom(18));

            Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 2 }))
                    .useAllAvailableWidth();
            table.setMarginBottom(18);

            addRow(table, "Customer", safe(user.getName()));
            addRow(table, "Email", safe(user.getEmail()));
            addRow(table, "Plan", safe(plan.getName()));
            addRow(table, "Transaction ID", safe(txn.getId()));
            addRow(table, "Order reference", safe(txn.getOrderId()));
            addRow(table, "Gateway payment ID", safe(txn.getPaymentId()));
            addRow(table, "Amount", safe(txn.getCurrency()) + " " + String.format("%,d", txn.getAmount()));
            addRow(table, "Status", txn.getStatus().toString());
            addRow(table, "Created", txn.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));
            if (!success) addRow(table, "Reason", safe(failureReason));

            document.add(table);
            document.add(new Paragraph("CareerForge AI payment record | Keep the transaction ID when contacting support.")
                    .setFontSize(9).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice PDF for order {}: {}", txn.getOrderId(), e.getMessage());
            throw new RuntimeException("Failed to generate invoice PDF: " + e.getMessage());
        }
    }

    private void addRow(Table table, String label, String value) {
        table.addCell(new Cell().setBackgroundColor(ROW)
                .add(new Paragraph(label).setBold().setFontSize(9).setFontColor(MUTED)));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(9)));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Not available" : value;
    }
}