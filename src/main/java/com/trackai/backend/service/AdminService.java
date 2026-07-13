package com.trackai.backend.service;

import com.trackai.backend.dto.ActionResponse;
import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.dto.admin.AdminMessageRequest;
import com.trackai.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminService {

    // Kept for internal compatibility; API uses the paginated safe DTO endpoint.
    List<User> getAllUsers();

    Page<User> getUsers(String search, Pageable pageable);

    User getUserById(String id);

    ActionResponse enableUser(String id);

    ActionResponse disableUser(String id);

    ActionResponse blockUser(String id);

    ActionResponse unblockUser(String id);

    ActionResponse deleteUser(String id);

    ActionResponse sendMessage(String id, AdminMessageRequest request);

    User getCurrentAdmin();

    UpdateProfileResponse updateCurrentAdmin(UpdateProfileRequest request);
}