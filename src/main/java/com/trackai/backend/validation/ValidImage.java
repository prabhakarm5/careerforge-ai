package com.trackai.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ImageValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidImage {

        String message() default "Only JPG, JPEG, PNG and WEBP images are allowed";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        // Default: 5 MB — override karo annotation mein zaroorat ho toh
        long maxSize() default 5 * 1024 * 1024;

        String[] allowedTypes() default {
                        "image/jpeg",
                        "image/jpg",
                        "image/png",
                        "image/webp"
        };
}