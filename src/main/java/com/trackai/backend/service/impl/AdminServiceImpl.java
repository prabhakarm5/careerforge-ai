package com.trackai.backend.service.impl;

import com.trackai.backend.dto.ActionResponse;
import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.AdminService;
import com.trackai.backend.service.CloudinaryService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl
                implements AdminService {

        private final UserRepository userRepository;

        private final CloudinaryService cloudinaryService;

        // GET ALL USERS
        @Override
        public List<User> getAllUsers() {

                return userRepository.findAll();
        }

        // GET USER BY ID
        @Override
        public User getUserById(
                        String id) {

                return userRepository.findById(id)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "User not found"));
        }

        // ENABLE USER
        @Override
        public ActionResponse enableUser(
                        String id) {

                User user = getUserById(id);

                // ADMIN ALREADY ENABLED
                if (user.getRole() == Role.ROLE_ADMIN) {

                        return ActionResponse.builder()

                                        .message(
                                                        "Admin account is always enabled")

                                        .action(
                                                        "ENABLE_USER")

                                        .userId(
                                                        user.getId())

                                        .userEmail(
                                                        user.getEmail())

                                        .status(false)

                                        .build();
                }

                // ALREADY ENABLED
                if (user.getEnabled()) {

                        return ActionResponse.builder()

                                        .message(
                                                        "User is already enabled")

                                        .action(
                                                        "ENABLE_USER")

                                        .userId(
                                                        user.getId())

                                        .userEmail(
                                                        user.getEmail())

                                        .status(false)

                                        .build();
                }

                // ENABLE USER
                user.setEnabled(true);

                userRepository.save(user);

                return ActionResponse.builder()

                                .message(
                                                "User enabled successfully")

                                .action(
                                                "ENABLE_USER")

                                .userId(
                                                user.getId())

                                .userEmail(
                                                user.getEmail())

                                .status(true)

                                .build();
        }

        // DISABLE USER
        @Override
        public ActionResponse disableUser(
                        String id) {

                User user = getUserById(id);

                // ADMIN PROTECTION
                if (user.getRole() == Role.ROLE_ADMIN) {

                        throw new RuntimeException(
                                        "Admin account cannot be disabled");
                }

                // ALREADY DISABLED
                if (!user.getEnabled()) {

                        return ActionResponse.builder()

                                        .message(
                                                        "User is already disabled")

                                        .action(
                                                        "DISABLE_USER")

                                        .userId(
                                                        user.getId())

                                        .userEmail(
                                                        user.getEmail())

                                        .status(false)

                                        .build();
                }

                // DISABLE USER
                user.setEnabled(false);

                userRepository.save(user);

                return ActionResponse.builder()

                                .message(
                                                "User disabled successfully")

                                .action(
                                                "DISABLE_USER")

                                .userId(
                                                user.getId())

                                .userEmail(
                                                user.getEmail())

                                .status(true)

                                .build();
        }

        // BLOCK USER
        @Override
        public ActionResponse blockUser(
                        String id) {

                User user = getUserById(id);

                // ADMIN PROTECTION
                if (user.getRole() == Role.ROLE_ADMIN) {

                        throw new RuntimeException(
                                        "Admin account cannot be blocked");
                }

                // ALREADY BLOCKED
                if (user.getBlocked()) {

                        return ActionResponse.builder()

                                        .message(
                                                        "User is already blocked")

                                        .action(
                                                        "BLOCK_USER")

                                        .userId(
                                                        user.getId())

                                        .userEmail(
                                                        user.getEmail())

                                        .status(false)

                                        .build();
                }

                // BLOCK USER
                user.setBlocked(true);

                userRepository.save(user);

                return ActionResponse.builder()

                                .message(
                                                "User blocked successfully")

                                .action(
                                                "BLOCK_USER")

                                .userId(
                                                user.getId())

                                .userEmail(
                                                user.getEmail())

                                .status(true)

                                .build();
        }

        // UNBLOCK USER
        @Override
        public ActionResponse unblockUser(
                        String id) {

                User user = getUserById(id);

                // ADMIN ALWAYS UNBLOCKED
                if (user.getRole() == Role.ROLE_ADMIN) {

                        return ActionResponse.builder()

                                        .message(
                                                        "Admin account is always unblocked")

                                        .action(
                                                        "UNBLOCK_USER")

                                        .userId(
                                                        user.getId())

                                        .userEmail(
                                                        user.getEmail())

                                        .status(false)

                                        .build();
                }

                // ALREADY UNBLOCKED
                if (!user.getBlocked()) {

                        return ActionResponse.builder()

                                        .message(
                                                        "User is already unblocked")

                                        .action(
                                                        "UNBLOCK_USER")

                                        .userId(
                                                        user.getId())

                                        .userEmail(
                                                        user.getEmail())

                                        .status(false)

                                        .build();
                }

                // UNBLOCK USER
                user.setBlocked(false);

                userRepository.save(user);

                return ActionResponse.builder()

                                .message(
                                                "User unblocked successfully")

                                .action(
                                                "UNBLOCK_USER")

                                .userId(
                                                user.getId())

                                .userEmail(
                                                user.getEmail())

                                .status(true)

                                .build();
        }

        // DELETE USER
        @Override
        public ActionResponse deleteUser(
                        String id) {

                User user = getUserById(id);

                // ADMIN PROTECTION
                if (user.getRole() == Role.ROLE_ADMIN) {

                        throw new RuntimeException(
                                        "Admin account cannot be deleted");
                }

                // DELETE USER
                userRepository.delete(user);

                return ActionResponse.builder()

                                .message(
                                                "User deleted successfully")

                                .action(
                                                "DELETE_USER")

                                .userId(
                                                user.getId())

                                .userEmail(
                                                user.getEmail())

                                .status(true)

                                .build();
        }

        // GET CURRENT ADMIN
        @Override
        public User getCurrentAdmin() {

                Authentication authentication =

                                SecurityContextHolder

                                                .getContext()

                                                .getAuthentication();

                String email = authentication.getName();

                User admin = userRepository

                                .findByEmail(email)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "Admin not found"));

                // ROLE CHECK
                if (admin.getRole() != Role.ROLE_ADMIN) {

                        throw new RuntimeException(
                                        "Access denied");
                }

                return admin;
        }

        // UPDATE CURRENT ADMIN
        @Override
        public UpdateProfileResponse updateCurrentAdmin(
                        UpdateProfileRequest request) {

                // GET ADMIN
                User admin = getCurrentAdmin();

                List<String> updatedFields = new ArrayList<>();

                List<String> restrictedFields = List.of(
                                "email",
                                "mobileNumber",
                                "password",
                                "role",
                                "enabled",
                                "blocked");

                // UPDATE NAME
                if (request.getName() != null
                                &&
                                !request.getName()

                                                .equals(admin.getName())) {

                        admin.setName(
                                        request.getName());

                        updatedFields.add(
                                        "name");
                }

                // UPDATE DESCRIPTION
                if (request.getDescription() != null
                                &&
                                !request.getDescription()

                                                .equals(admin.getDescription())) {

                        admin.setDescription(
                                        request.getDescription());

                        updatedFields.add(
                                        "description");
                }

                // UPDATE IMAGE
                if (request.getProfileImage() != null
                                &&
                                !request.getProfileImage()
                                                .isEmpty()) {

                        // DELETE OLD IMAGE
                        if (admin.getProfileImage() != null
                                        &&
                                        !admin.getProfileImage()
                                                        .isBlank()) {

                                cloudinaryService.deleteImage(
                                                admin.getProfileImage());
                        }

                        // UPLOAD NEW IMAGE
                        CloudinaryUploadResponse upload =

                                        cloudinaryService.uploadProfileImage(

                                                        request.getProfileImage()

                                        );

                        admin.setProfileImage(

                                        upload.getSecureUrl()

                        );

                        updatedFields.add(
                                        "profileImage");
                }

                // SAVE ADMIN
                userRepository.save(admin);

                return UpdateProfileResponse.builder()

                                .message(
                                                "Admin profile updated successfully")

                                .updatedFields(
                                                updatedFields)

                                .restrictedFields(
                                                restrictedFields)

                                .profileImage(
                                                admin.getProfileImage())

                                .build();
        }
}