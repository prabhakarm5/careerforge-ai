package com.trackai.backend.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "trackai.tokens")
public class TokenProperties {

    private Long chat;

    private Long image;

    // Legacy fallback for deployments that have not split Resume AI billing yet.
    private Long resume;

    private Long resumeAnalysis;

    private Long resumeChat;

    private Long resumeMatch;

    private Long resumeGenerate;

    private Long coverLetter;

    private Long interviewStart;

    private Long interviewAnswer;

    private Long interviewLive;

    private Long interviewContext;

    private Long website;

    private Long pdf;

    private Long audio;

}
