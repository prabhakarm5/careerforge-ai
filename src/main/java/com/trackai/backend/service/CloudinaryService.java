package com.trackai.backend.service;

import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {

        CloudinaryUploadResponse uploadProfileImage(
                        MultipartFile file);

        CloudinaryUploadResponse uploadGeneratedImage(
                        byte[] imageBytes);

        CloudinaryUploadResponse uploadResume(
                        MultipartFile file);

        void deleteImage(
                        String publicId);

}