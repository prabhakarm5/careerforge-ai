package com.trackai.backend.service;

import com.trackai.backend.dto.ActionResponse;
import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.entity.User;

import java.util.List;

public interface AdminService {

    // GET ALL USERS
    List<User> getAllUsers();

    // GET USER BY ID
    User getUserById(
            String id);

    // ENABLE USER
    ActionResponse enableUser(
            String id);

    // DISABLE USER
    ActionResponse disableUser(
            String id);

    // BLOCK USER
    ActionResponse blockUser(
            String id);

    // UNBLOCK USER
    ActionResponse unblockUser(
            String id);

    // DELETE USER
    ActionResponse deleteUser(
            String id);

    // GET CURRENT ADMIN
    User getCurrentAdmin();

    // UPDATE CURRENT ADMIN
    UpdateProfileResponse updateCurrentAdmin(
            UpdateProfileRequest request);
}