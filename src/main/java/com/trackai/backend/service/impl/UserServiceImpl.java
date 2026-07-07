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
         * JWT -> Email -> Redis
         * HIT -> CachedUser se User bana ke return (NO DB CALL) ✅
         * MISS -> Database -> Save Redis -> Return
         *
         * FIX: pehle cache HIT hone par bhi userRepository.findByEmail()
         * call ho raha tha, jisse Redis cache ka koi fayda nahi mil raha
         * tha aur har dashboard/profile load par DB hit ho raha tha.
         * Ab cache hit pe seedha CachedUser se User object banta hai,
         * DB tak jaata hi nahi.
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

                        return User.builder()
                                        .id(cachedUser.getId())
                                        .name(cachedUser.getName())
                                        .email(cachedUser.getEmail())
                                        .role(cachedUser.getRole())
                                        .enabled(cachedUser.getEnabled())
                                        .blocked(cachedUser.getBlocked())
                                        .emailVerified(cachedUser.getEmailVerified())
                                        .mobileNumber(cachedUser.getMobileNumber())
                                        .profileImage(cachedUser.getProfileImage())
                                        // FIX: publicId bhi map karo, warna updateCurrentUser()
                                        // mein oldPublicId hamesha null milegi cache-hit path pe
                                        .profileImagePublicId(cachedUser.getProfileImagePublicId())
                                        .description(cachedUser.getDescription())
                                        .createdAt(cachedUser.getCreatedAt())
                                        .build();

                }

                /*
                 * STEP-2
                 * DATABASE (sirf cache MISS pe yahan aayega)
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
                                        // FIX: DB se cache banate waqt bhi publicId include karo
                                        .profileImagePublicId(user.getProfileImagePublicId())
                                        .description(user.getDescription())
                                        .createdAt(user.getCreatedAt())
                                        .build();

                        redisUserCacheService.saveUser(cache);

                }

                return user;

        }

        @Override
        public User getCurrentUser() {

                return getAuthenticatedUser();
        }

        @Override
        public UpdateProfileResponse updateCurrentUser(UpdateProfileRequest request) {

                User user = getAuthenticatedUser();

                List<String> updatedFields = new ArrayList<>();
                List<String> restrictedFields = new ArrayList<>();

                if (request.getEmail() != null &&
                                !request.getEmail().equals(user.getEmail())) {
                        restrictedFields.add("email");
                }

                if (request.getMobileNumber() != null &&
                                !request.getMobileNumber().equals(user.getMobileNumber())) {
                        restrictedFields.add("mobileNumber");
                }

                if (request.getPassword() != null &&
                                !request.getPassword().isBlank()) {
                        restrictedFields.add("password");
                }

                if (request.getName() != null &&
                                !request.getName().equals(user.getName())) {
                        user.setName(request.getName());
                        updatedFields.add("name");
                }

                if (request.getDescription() != null &&
                                !request.getDescription().equals(user.getDescription())) {
                        user.setDescription(request.getDescription());
                        updatedFields.add("description");
                }

                MultipartFile profileImage = request.getProfileImage();

                if (profileImage != null &&
                                profileImage.getOriginalFilename() != null &&
                                !profileImage.getOriginalFilename().isBlank() &&
                                !profileImage.isEmpty()) {

                        String oldPublicId = user.getProfileImagePublicId();

                        CloudinaryUploadResponse upload = cloudinaryService.uploadProfileImage(profileImage);

                        user.setProfileImage(upload.getSecureUrl());
                        user.setProfileImagePublicId(upload.getPublicId());

                        updatedFields.add("profileImage");

                        if (oldPublicId != null && !oldPublicId.isBlank()) {
                                cloudinaryService.deleteImage(oldPublicId);
                        }
                }

                /*
                 * NOTE: getAuthenticatedUser() ab cache-hit case mein DB se
                 * DETACHED User object return karta hai. userRepository.save()
                 * ko phir bhi call karna zaroori hai taaki changes DB mein
                 * persist ho (JPA yahan save() ke through hi update karega,
                 * dirty-checking cache-hit path pe nahi chalegi kyunki
                 * object DB-managed session se attached nahi hai).
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
                                        // FIX: naya publicId cache mein bhi update karo,
                                        // warna agla profile-image-update purani (ab-invalid)
                                        // publicId use karega
                                        .profileImagePublicId(updatedUser.getProfileImagePublicId())
                                        .description(updatedUser.getDescription())
                                        .createdAt(updatedUser.getCreatedAt())
                                        .build();

                        redisUserCacheService.updateUser(cachedUser1);
                }
                if (updatedFields.isEmpty() && restrictedFields.isEmpty()) {

                        return UpdateProfileResponse.builder()
                                        .message("No changes detected")
                                        .updatedFields(updatedFields)
                                        .restrictedFields(restrictedFields)
                                        .profileImage(updatedUser.getProfileImage())
                                        .build();
                }

                if (updatedFields.isEmpty() && !restrictedFields.isEmpty()) {

                        return UpdateProfileResponse.builder()
                                        .message("Restricted fields cannot be updated")
                                        .updatedFields(updatedFields)
                                        .restrictedFields(restrictedFields)
                                        .profileImage(updatedUser.getProfileImage())
                                        .build();
                }

                return UpdateProfileResponse.builder()
                                .message("Profile updated successfully")
                                .updatedFields(updatedFields)
                                .restrictedFields(restrictedFields)
                                .profileImage(updatedUser.getProfileImage())
                                .build();
        }
}