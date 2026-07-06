package com.trackai.backend.service.impl;

import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.dto.cache.CachedUser;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import com.trackai.backend.service.CloudinaryService;
import com.trackai.backend.service.RedisUserCacheService;
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
public class UserServiceImpl implements UserService {

        private final UserRepository userRepository;

        private final CloudinaryService cloudinaryService;

        private final RedisUserCacheService redisUserCacheService;

        /*
         * ==========================================================
         * GET AUTHENTICATED USER
         *
         * JWT
         * ↓
         * Email
         * ↓
         * Redis
         * ↓
         * HIT -> Return
         *
         * MISS
         * ↓
         * Database
         * ↓
         * Save Redis
         * ↓
         * Return
         * ==========================================================
         */
        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String email = authentication.getName();

                /*
                 * STEP-1
                 * REDIS
                 */
                CachedUser cachedUser = redisUserCacheService.getUser(email);

                if (cachedUser != null) {

                        return userRepository
                                        .findByEmail(email)
                                        .orElseThrow(() -> new RuntimeException("User not found"));
                }

                /*
                 * STEP-2
                 * DATABASE
                 */
                User user = userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                /*
                 * STEP-3
                 * SAVE CACHE
                 */
                if (user.getRole() != Role.ROLE_ADMIN) {

                        CachedUser cache = CachedUser.builder()
                                        .id(user.getId())
                                        .name(user.getName())
                                        .email(user.getEmail())
                                        .role(user.getRole())
                                        .enabled(user.getEnabled())
                                        .blocked(user.getBlocked())
                                        .emailVerified(user.getEmailVerified())
                                        .mobileNumber(user.getMobileNumber())
                                        .profileImage(user.getProfileImage())
                                        .description(user.getDescription())
                                        .createdAt(user.getCreatedAt())
                                        .build();

                        redisUserCacheService.saveUser(cache);
                }

                return user;
        }

        /*
         * ==========================================================
         * GET CURRENT USER
         * ==========================================================
         */
        @Override
        public User getCurrentUser() {

                return getAuthenticatedUser();
        }

        /*
         * ==========================================================
         * UPDATE PROFILE
         * ==========================================================
         */
        @Override
        public UpdateProfileResponse updateCurrentUser(
                        UpdateProfileRequest request) {

                User user = getAuthenticatedUser();

                List<String> updatedFields = new ArrayList<>();

                List<String> restrictedFields = new ArrayList<>();

                /*
                 * EMAIL
                 */
                if (request.getEmail() != null &&
                                !request.getEmail().equals(user.getEmail())) {

                        restrictedFields.add("email");
                }

                /*
                 * MOBILE
                 */
                if (request.getMobileNumber() != null &&
                                !request.getMobileNumber().equals(user.getMobileNumber())) {

                        restrictedFields.add("mobileNumber");
                }

                /*
                 * PASSWORD
                 */
                if (request.getPassword() != null &&
                                !request.getPassword().isBlank()) {

                        restrictedFields.add("password");
                }

                /*
                 * NAME
                 */
                if (request.getName() != null &&
                                !request.getName().equals(user.getName())) {

                        user.setName(request.getName());

                        updatedFields.add("name");
                }

                /*
                 * DESCRIPTION
                 */
                if (request.getDescription() != null &&
                                !request.getDescription().equals(user.getDescription())) {

                        user.setDescription(request.getDescription());

                        updatedFields.add("description");
                }

                /*
                 * PROFILE IMAGE UPDATE LOGIC
                 */
                MultipartFile profileImage = request.getProfileImage();

                if (profileImage != null &&
                                profileImage.getOriginalFilename() != null &&
                                !profileImage.getOriginalFilename().isBlank() &&
                                !profileImage.isEmpty()) {

                        // STEP 1: Pehle purana publicId nikaal ke ek local variable mein rakho
                        // (URL nahi, wo alag "profileImagePublicId" column se aayega)
                        String oldPublicId = user.getProfileImagePublicId();

                        // STEP 2: Naya image Cloudinary pe upload karo
                        CloudinaryUploadResponse upload = cloudinaryService.uploadProfileImage(profileImage);

                        // STEP 3: User entity mein DONO cheezein update karo -> URL (display ke liye)
                        // aur publicId (future delete/update ke liye)
                        user.setProfileImage(upload.getSecureUrl());
                        user.setProfileImagePublicId(upload.getPublicId());

                        updatedFields.add("profileImage");

                        // STEP 4: Naya image set hone ke BAAD, purana image Cloudinary se delete karo
                        // (agar purana publicId maujood tha)
                        if (oldPublicId != null && !oldPublicId.isBlank()) {
                                cloudinaryService.deleteImage(oldPublicId); // <-- ab sahi publicId jaayega, URL nahi
                        }
                }

                /*
                 * SAVE DATABASE
                 */
                User updatedUser = userRepository.save(user);
                if (updatedUser.getRole() != Role.ROLE_ADMIN) {

                        CachedUser cachedUser1 = CachedUser.builder()
                                        .id(updatedUser.getId())
                                        .name(updatedUser.getName())
                                        .email(updatedUser.getEmail())
                                        .role(updatedUser.getRole())
                                        .enabled(updatedUser.getEnabled())
                                        .blocked(updatedUser.getBlocked())
                                        .emailVerified(updatedUser.getEmailVerified())
                                        .mobileNumber(updatedUser.getMobileNumber())
                                        .profileImage(updatedUser.getProfileImage())
                                        .description(updatedUser.getDescription())
                                        .createdAt(updatedUser.getCreatedAt())
                                        .build();

                        redisUserCacheService.updateUser(cachedUser1);
                }

                /*
                 * NO CHANGES
                 */
                if (updatedFields.isEmpty() && restrictedFields.isEmpty())

                {

                        return UpdateProfileResponse.builder()
                                        .message("No changes detected")
                                        .updatedFields(updatedFields)
                                        .restrictedFields(restrictedFields)
                                        .profileImage(updatedUser.getProfileImage())
                                        .build();
                }

                /*
                 * ONLY RESTRICTED
                 */
                if (updatedFields.isEmpty() && !restrictedFields.isEmpty()) {

                        return UpdateProfileResponse.builder()
                                        .message("Restricted fields cannot be updated")
                                        .updatedFields(updatedFields)
                                        .restrictedFields(restrictedFields)
                                        .profileImage(updatedUser.getProfileImage())
                                        .build();
                }

                /*
                 * SUCCESS
                 */
                return UpdateProfileResponse.builder().message("Profile updated successfully")
                                .updatedFields(updatedFields).restrictedFields(restrictedFields)
                                .profileImage(updatedUser.getProfileImage()).build();
        }
}