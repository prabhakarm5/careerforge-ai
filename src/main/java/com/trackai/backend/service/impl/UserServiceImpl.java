package com.trackai.backend.service.impl;

import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.CloudinaryService;
import com.trackai.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl
                implements UserService {

        private final UserRepository userRepository;

        private final CloudinaryService cloudinaryService;

        // GET CURRENT AUTHENTICATED USER
        private User getAuthenticatedUser() {

                Authentication authentication =

                                SecurityContextHolder
                                                .getContext()
                                                .getAuthentication();

                String email = authentication.getName();

                return userRepository

                                .findByEmail(email)

                                .orElseThrow(() ->

                                new RuntimeException(
                                                "User not found"));
        }

        // GET CURRENT USER
        @Override
        public User getCurrentUser() {

                return getAuthenticatedUser();
        }

        // UPDATE CURRENT USER
        @Override
        public UpdateProfileResponse updateCurrentUser(
                        UpdateProfileRequest request) {

                // GET CURRENT USER
                User user = getAuthenticatedUser();

                // UPDATED FIELDS
                List<String> updatedFields = new ArrayList<>();

                // RESTRICTED FIELDS
                List<String> restrictedFields = new ArrayList<>();

                // RESTRICT EMAIL CHANGE
                if (request.getEmail() != null
                                &&
                                !request.getEmail().equals(
                                                user.getEmail())) {

                        restrictedFields.add(
                                        "email");
                }

                // RESTRICT MOBILE CHANGE
                if (request.getMobileNumber() != null
                                &&
                                !request.getMobileNumber().equals(
                                                user.getMobileNumber())) {

                        restrictedFields.add(
                                        "mobileNumber");
                }

                // RESTRICT PASSWORD CHANGE
                if (request.getPassword() != null
                                &&
                                !request.getPassword().isBlank()) {

                        restrictedFields.add(
                                        "password");
                }

                // UPDATE NAME
                if (request.getName() != null
                                &&
                                !request.getName().equals(
                                                user.getName())) {

                        user.setName(
                                        request.getName());

                        updatedFields.add(
                                        "name");
                }

                // UPDATE DESCRIPTION
                if (request.getDescription() != null
                                &&
                                !request.getDescription().equals(
                                                user.getDescription())) {

                        user.setDescription(
                                        request.getDescription());

                        updatedFields.add(
                                        "description");
                }

                // PROFILE IMAGE
                MultipartFile profileImage = request.getProfileImage();

                // IMAGE EXISTS
                if (profileImage != null
                                &&
                                profileImage.getOriginalFilename() != null
                                &&
                                !profileImage.getOriginalFilename().isBlank()
                                &&
                                !profileImage.isEmpty()) {

                        // DELETE OLD IMAGE
                        if (user.getProfileImage() != null
                                        &&
                                        !user.getProfileImage().isBlank()) {

                                cloudinaryService
                                                .deleteImage(

                                                                user.getProfileImage());
                        }

                        // UPLOAD NEW IMAGE
                        CloudinaryUploadResponse upload =

                                        cloudinaryService

                                                        .uploadProfileImage(profileImage);

                        // save
                        user.setProfileImage(

                                        upload.getSecureUrl()

                        );

                        updatedFields.add(
                                        "profileImage");
                }

                // SAVE USER
                User updatedUser = userRepository.save(user);

                // NO CHANGES
                if (updatedFields.isEmpty()
                                &&
                                restrictedFields.isEmpty()) {

                        return UpdateProfileResponse.builder()

                                        .message(
                                                        "No changes detected")

                                        .updatedFields(
                                                        updatedFields)

                                        .restrictedFields(
                                                        restrictedFields)

                                        .profileImage(
                                                        updatedUser.getProfileImage())

                                        .build();
                }

                // ONLY RESTRICTED CHANGES
                if (updatedFields.isEmpty()
                                &&
                                !restrictedFields.isEmpty()) {

                        return UpdateProfileResponse.builder()

                                        .message(
                                                        "Restricted fields cannot be updated")

                                        .updatedFields(
                                                        updatedFields)

                                        .restrictedFields(
                                                        restrictedFields)

                                        .profileImage(
                                                        updatedUser.getProfileImage())

                                        .build();
                }

                // SUCCESS RESPONSE
                return UpdateProfileResponse.builder()

                                .message(
                                                "Profile updated successfully")

                                .updatedFields(
                                                updatedFields)

                                .restrictedFields(
                                                restrictedFields)

                                .profileImage(
                                                updatedUser.getProfileImage())

                                .build();
        }
}