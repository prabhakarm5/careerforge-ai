package com.trackai.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

public class ImageValidator implements ConstraintValidator<ValidImage, MultipartFile> {

    private long maxSize;
    private String[] allowedTypes;

    @Override
    public void initialize(ValidImage constraintAnnotation) {
        this.maxSize = constraintAnnotation.maxSize();
        this.allowedTypes = constraintAnnotation.allowedTypes();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {

        // File optional hai — null/empty allowed
        if (file == null || file.isEmpty()) {
            return true;
        }

        // ── Size check ───────────────────────────────────────────────────
        if (file.getSize() > maxSize) {
            // Bytes → MB (rounded to 1 decimal), human-readable message
            double limitMb = maxSize / (1024.0 * 1024.0);
            double actualMb = file.getSize() / (1024.0 * 1024.0);

            String msg = String.format(
                    "Image is too large (%.1f MB). Maximum allowed size is %.0f MB.",
                    actualMb, limitMb);

            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
            return false;
        }

        // ── Content-type present? ────────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Could not determine image type. Please upload a valid image file.").addConstraintViolation();
            return false;
        }

        // ── Allowed type check ───────────────────────────────────────────
        if (!Arrays.asList(allowedTypes).contains(contentType)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Unsupported format '" + contentType + "'. Only JPG, PNG, and WEBP images are allowed.")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}