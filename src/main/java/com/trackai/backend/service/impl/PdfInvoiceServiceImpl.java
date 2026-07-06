package com.trackai.backend.service.impl;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
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

    @Override
    public byte[] generateInvoice(User user, PaymentTransaction txn, SubscriptionPlan plan,
            boolean success, String failureReason) {

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
            Document document = new Document(pdfDoc);

            document.add(
                    new Paragraph("TrackAI")
                            .setBold()
                            .setFontSize(20));

            document.add(
                    new Paragraph(success ? "Payment Invoice" : "Payment Failed Notice")
                            .setFontSize(14)
                            .setFontColor(success ? ColorConstants.GREEN : ColorConstants.RED));

            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 2 }))
                    .useAllAvailableWidth();

            addRow(table, "Customer Name", user.getName());
            addRow(table, "Email", user.getEmail());
            addRow(table, "Plan", plan.getName());
            addRow(table, "Order ID", txn.getOrderId());
            addRow(table, "Payment ID", txn.getPaymentId() != null ? txn.getPaymentId() : "N/A");
            addRow(table, "Amount", txn.getCurrency() + " " + txn.getAmount());
            addRow(table, "Status", txn.getStatus().toString());
            addRow(table, "Date",
                    txn.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));

            if (!success) {
                addRow(table, "Failure Reason", failureReason != null ? failureReason : "Unknown");
            }

            document.add(table);
            document.add(new Paragraph(" "));

            document.add(
                    new Paragraph("This is a system-generated document from TrackAI.")
                            .setFontSize(9)
                            .setFontColor(ColorConstants.GRAY));

            document.close();

            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate invoice PDF for order {}: {}", txn.getOrderId(), e.getMessage());
            throw new RuntimeException("Failed to generate invoice PDF: " + e.getMessage());
        }
    }

    private void addRow(Table table, String label, String value) {
        table.addCell(label);
        table.addCell(value);
    }
}