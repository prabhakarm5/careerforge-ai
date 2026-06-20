package com.trackai.backend.controller;

import com.trackai.backend.dto.ActionResponse;
import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

        private final AdminService adminService;

        // GET CURRENT ADMIN PROFILE
        @GetMapping("/me")
        public ResponseEntity<User> getCurrentAdmin() {

                return ResponseEntity.ok(

                                adminService
                                                .getCurrentAdmin());
        }

        // UPDATE CURRENT ADMIN PROFILE
        @PutMapping(

                        value = "/me",

                        consumes = "multipart/form-data")
        public ResponseEntity<UpdateProfileResponse> updateCurrentAdmin(

                        @ModelAttribute @Valid UpdateProfileRequest request) {

                return ResponseEntity.ok(

                                adminService
                                                .updateCurrentAdmin(
                                                                request));
        }

        // GET ALL USERS
        @GetMapping("/users")
        public ResponseEntity<List<User>> getAllUsers() {

                return ResponseEntity.ok(

                                adminService
                                                .getAllUsers());
        }

        // GET USER BY ID
        @GetMapping("/users/{id}")
        public ResponseEntity<User> getUserById(

                        @PathVariable String id) {

                return ResponseEntity.ok(

                                adminService
                                                .getUserById(id));
        }

        // ENABLE USER
        @PutMapping("/users/{id}/enable")
        public ResponseEntity<ActionResponse> enableUser(

                        @PathVariable String id) {

                return ResponseEntity.ok(

                                adminService
                                                .enableUser(id));
        }

        // DISABLE USER
        @PutMapping("/users/{id}/disable")
        public ResponseEntity<ActionResponse> disableUser(

                        @PathVariable String id) {

                return ResponseEntity.ok(

                                adminService
                                                .disableUser(id));
        }

        // BLOCK USER
        @PutMapping("/users/{id}/block")
        public ResponseEntity<ActionResponse> blockUser(

                        @PathVariable String id) {

                return ResponseEntity.ok(

                                adminService
                                                .blockUser(id));
        }

        // UNBLOCK USER
        @PutMapping("/users/{id}/unblock")
        public ResponseEntity<ActionResponse> unblockUser(

                        @PathVariable String id) {

                return ResponseEntity.ok(

                                adminService
                                                .unblockUser(id));
        }

        // DELETE USER
        @DeleteMapping("/users/{id}")
        public ResponseEntity<ActionResponse> deleteUser(

                        @PathVariable String id) {

                return ResponseEntity.ok(

                                adminService
                                                .deleteUser(id));
        }
}