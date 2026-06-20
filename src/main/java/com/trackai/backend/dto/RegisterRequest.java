package com.trackai.backend.dto;

import org.springframework.web.multipart.MultipartFile;

import com.trackai.backend.validation.ValidImage;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    @Size(max = 500, message = "Description too long")
    private String description;

    @ValidImage
    private MultipartFile profileImage;
}