package com.trackai.backend.service;

import com.trackai.backend.dto.admin.AdminSystemStatusResponse;

public interface AdminSystemService {
    AdminSystemStatusResponse getStatus();
}