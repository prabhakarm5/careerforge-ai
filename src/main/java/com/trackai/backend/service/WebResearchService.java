package com.trackai.backend.service;

public interface WebResearchService {
    String researchIfNeeded(String query);
    String research(String query, boolean force);
}