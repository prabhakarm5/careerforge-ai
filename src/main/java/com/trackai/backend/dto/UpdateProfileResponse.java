package com.trackai.backend.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class UpdateProfileResponse {

    private String message;

    private List<String> updatedFields;

    private List<String> restrictedFields;

    private String profileImage;
}