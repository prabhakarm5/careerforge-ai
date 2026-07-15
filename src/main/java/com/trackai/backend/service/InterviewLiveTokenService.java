package com.trackai.backend.service;

import com.trackai.backend.dto.interview.LiveInterviewTokenRequest;
import com.trackai.backend.dto.interview.LiveInterviewTokenResponse;

public interface InterviewLiveTokenService {
    LiveInterviewTokenResponse create(LiveInterviewTokenRequest request);
}
