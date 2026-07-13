package com.trackai.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResumePdfServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumePdfService pdfService = new ResumePdfService();

    @Test
    void createsReadableAtsPdf() throws Exception {
        JsonNode resume = objectMapper.readTree("""
                {
                  "name": "Prabhakar Mishra",
                  "headline": "Java Backend Engineer",
                  "contact": {
                    "email": "candidate@example.com",
                    "phone": "+91 9999999999",
                    "location": "India",
                    "linkedin": "linkedin.com/in/candidate",
                    "portfolio": ""
                  },
                  "summary": "Backend engineer building secure and reliable APIs.",
                  "skills": ["Java", "Spring Boot", "PostgreSQL"],
                  "experience": [{
                    "title": "Software Engineer",
                    "company": "Example",
                    "startDate": "2024",
                    "endDate": "Present",
                    "location": "Remote",
                    "bullets": ["Improved API latency by 30%."]
                  }],
                  "projects": [],
                  "education": [],
                  "certifications": []
                }
                """);

        byte[] pdf = pdfService.create(resume);

        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
