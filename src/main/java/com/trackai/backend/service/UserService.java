package com.trackai.backend.service;

import org.springframework.web.multipart.MultipartFile;
import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.entity.User;

public interface UserService {

    // CURRENT USER PROFILE
    User getCurrentUser();

    // UPDATE CURRENT USER
    UpdateProfileResponse updateCurrentUser(
            UpdateProfileRequest request);

    // User uploadProfileImage(
    // MultipartFile file);

}