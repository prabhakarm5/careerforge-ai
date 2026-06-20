package com.trackai.backend.dto;

import lombok.*;

@Getter
@Setter

@NoArgsConstructor
@AllArgsConstructor

@Builder
public class ActionResponse {

    private String message;

    private String action;

    private String userId;

    private String userEmail;

    private boolean status;
}