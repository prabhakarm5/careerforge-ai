package com.trackai.backend.service;

import com.trackai.backend.dto.admin.AdminMonitoringResponse;
import com.trackai.backend.dto.admin.AdminRequestLogPageResponse;
import com.trackai.backend.dto.admin.AdminUserActivityResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AdminMonitoringService {

    void recordRequest(
            HttpServletRequest request,
            int status,
            long durationMs,
            String contentType,
            long responseBytes);

    void recordPageView(
            String rawPath,
            String timezone,
            String locale,
            HttpServletRequest request);

    AdminMonitoringResponse overview();

    AdminUserActivityResponse activityForUser(String userId);

    AdminRequestLogPageResponse requestLogs(
            int page,
            int size,
            String user,
            Integer status,
            String path);
}