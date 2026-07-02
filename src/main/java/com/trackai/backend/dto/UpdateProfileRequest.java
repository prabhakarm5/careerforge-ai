package com.trackai.backend.dto;

import com.trackai.backend.validation.ValidImage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class UpdateProfileRequest {

        @Size(

                        min = 3,

                        max = 50,

                        message = "Name must be between 3 and 50 characters")
        private String name;

        @Size(

                        max = 500,

                        message = "Description cannot exceed 500 characters")
        private String description;

        // RESTRICTED FIELDS
        private String email;

        private String mobileNumber;

        private String password;

        // PROFILE IMAGE
        @ValidImage
        private MultipartFile profileImage;
}