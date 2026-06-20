package com.trackai.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

public class ImageValidator

        implements ConstraintValidator<ValidImage, MultipartFile> {

    private long maxSize;

    private String[] allowedTypes;

    @Override
    public void initialize(
            ValidImage constraintAnnotation) {

        this.maxSize = constraintAnnotation.maxSize();

        this.allowedTypes = constraintAnnotation.allowedTypes();
    }

    @Override
    public boolean isValid(

            MultipartFile file,

            ConstraintValidatorContext context) {

        // OPTIONAL IMAGE
        if (file == null
                ||
                file.isEmpty()) {

            return true;
        }

        // FILE SIZE CHECK
        if (file.getSize() > maxSize) {

            context.disableDefaultConstraintViolation();

            context.buildConstraintViolationWithTemplate(

                    "Image size cannot exceed 5 MB"

            ).addConstraintViolation();

            return false;
        }

        // CONTENT TYPE
        String contentType = file.getContentType();

        // INVALID FILE
        if (contentType == null) {

            context.disableDefaultConstraintViolation();

            context.buildConstraintViolationWithTemplate(

                    "Invalid image file"

            ).addConstraintViolation();

            return false;
        }

        // INVALID TYPE
        if (!Arrays.asList(
                allowedTypes).contains(contentType)) {

            context.disableDefaultConstraintViolation();

            context.buildConstraintViolationWithTemplate(

                    "Only JPG, JPEG, PNG and WEBP images are allowed"

            ).addConstraintViolation();

            return false;
        }

        return true;
    }
}