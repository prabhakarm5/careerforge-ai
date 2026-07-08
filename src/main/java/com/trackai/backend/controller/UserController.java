package com.trackai.backend.controller;

import com.trackai.backend.dto.UpdateProfileRequest;
import com.trackai.backend.dto.UpdateProfileResponse;
import com.trackai.backend.entity.User;
import com.trackai.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

        private final UserService userService;

        // GET CURRENT LOGGED-IN USER
        @GetMapping("/me")
        public ResponseEntity<User> getCurrentUser() {

                return ResponseEntity.ok(

                                userService
                                                .getCurrentUser());
        }

        // UPDATE CURRENT LOGGED-IN USER
        @PutMapping(

                        value = "/me",

                        consumes = "multipart/form-data")
        public ResponseEntity<UpdateProfileResponse> updateCurrentUser(

                        @ModelAttribute @Valid UpdateProfileRequest request) {

                return ResponseEntity.ok(

                                userService
                                                .updateCurrentUser(
                                                                request));
        }
}