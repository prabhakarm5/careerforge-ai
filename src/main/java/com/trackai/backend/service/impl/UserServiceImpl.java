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
         * GET AUTHENTICATED USER (READ-ONLY PATH — cache-first)
         *
         * JWT -> Email -> Redis
         * HIT -> CachedUser se User bana ke return (NO DB CALL) ✅
         * MISS -> Database -> Save Redis -> Return
         *
         * ⚠️ IMPORTANT: Ye method sirf READ (getCurrentUser) ke liye hai.
         * CachedUser mein "password" field jaan-boojhke store nahi hoti
         * (security ke liye), isliye cache-hit case mein yahan se return
         * hua User object ki password hamesha NULL hogi.
         *
         * Isi wajah se pehle bug tha: updateCurrentUser() isi method ka
         * result seedha userRepository.save() ko de raha tha — cache-hit
         * hote hi save() ne password column ko NULL se overwrite kar diya,
         * jisse DB ka NOT NULL constraint tootke poora PUT /api/users/me
         * fail ho raha tha (aur isliye profile update kabhi hota hi nahi
         * tha).
         *
         * FIX: updateCurrentUser() ab is method ki jagah niche wali
         * getManagedUserForUpdate() use karta hai, jo hamesha DB se
         * poora entity (password samet) laata hai.
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
                                        .profileImagePublicId(user.getProfileImagePublicId())
                                        .description(user.getDescription())
                                        .createdAt(user.getCreatedAt())
                                        .build();

                        redisUserCacheService.saveUser(cache);

                }

                return user;

        }

        /*
         * ==========================================================
         * GET MANAGED USER FOR UPDATE (DB-ONLY — NO CACHE)
         *
         * ✅ FIX: Kisi bhi write/update operation ke liye hamesha DB se
         * hi poora entity fetch karo — password, passwordChangedAt, jaisi
         * fields jo CachedUser mein store hi nahi hoti, unhe save() ke
         * dauraan accidental NULL hone se bachane ke liye.
         *
         * Read path (getCurrentUser) abhi bhi cache-first rahega taaki
         * dashboard/profile GET fast rahe — sirf UPDATE ke waqt hi is
         * "safe" method ka use hoga.
         * ==========================================================
         */
        private User getManagedUserForUpdate() {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String email = authentication.getName();

                return userRepository
                                .findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        @Override
        public User getCurrentUser() {

                return getAuthenticatedUser();
        }

        @Override
        public UpdateProfileResponse updateCurrentUser(UpdateProfileRequest request) {

                // ✅ FIX — cache-built (possibly password-null) object ki
                // jagah, hamesha DB se seedha managed entity lo
                User user = getManagedUserForUpdate();

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
                 * ✅ Ab "user" hamesha DB-managed, poori fields wali entity
                 * hai (password samet), isliye save() koi bhi column NULL
                 * nahi karega — sirf upar jo fields explicitly set ki
                 * gayi hain wahi update hongi.
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
                                        .profileImagePublicId(updatedUser.getProfileImagePublicId())
                                        .description(updatedUser.getDescription())
                                        .createdAt(updatedUser.getCreatedAt())
                                        .build();

                        // ✅ Update ke baad cache ko bhi turant refresh karo,
                        // taaki agla GET /api/users/me (chahe wo refresh-token
                        // rotation ke turant baad hi kyun na aaye) purana/stale
                        // data na dikhaye — Redis mein latest values turant
                        // overwrite ho jaate hain.
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