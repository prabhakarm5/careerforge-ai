package com.trackai.backend.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.trackai.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl

                implements CloudinaryService {

        private final Cloudinary cloudinary;

        // UPLOAD IMAGE
        @Override
        public String uploadImage(
                        MultipartFile file) {

                try {

                        // FILE NAME
                        String fileName = file.getOriginalFilename();

                        // INVALID FILE NAME
                        if (fileName == null
                                        ||
                                        fileName.contains("..")) {

                                throw new RuntimeException(
                                                "Invalid image file name");
                        }

                        // UPLOAD IMAGE
                        Map uploadResult =

                                        cloudinary.uploader()

                                                        .upload(

                                                                        file.getBytes(),

                                                                        ObjectUtils.asMap(

                                                                                        "folder",
                                                                                        "trackai/profile-images",

                                                                                        "public_id",

                                                                                        UUID.randomUUID()
                                                                                                        .toString()));

                        // RETURN IMAGE URL
                        return uploadResult

                                        .get("secure_url")

                                        .toString();

                } catch (Exception e) {

                        throw new RuntimeException(

                                        "Image upload failed: "
                                                        + e.getMessage());
                }
        }

        // DELETE IMAGE
        @Override
        public void deleteImage(
                        String imageUrl) {

                try {

                        // NULL CHECK
                        if (imageUrl == null
                                        ||
                                        imageUrl.isBlank()) {

                                return;
                        }

                        // EXTRACT PUBLIC ID
                        String publicId =

                                        imageUrl

                                                        .substring(

                                                                        imageUrl.indexOf(
                                                                                        "trackai/profile-images/"));

                        // REMOVE EXTENSION
                        publicId = publicId

                                        .replace(".jpg", "")
                                        .replace(".jpeg", "")
                                        .replace(".png", "")
                                        .replace(".webp", "");

                        // DELETE IMAGE
                        cloudinary.uploader()

                                        .destroy(

                                                        publicId,

                                                        ObjectUtils.emptyMap());

                } catch (Exception e) {

                        throw new RuntimeException(

                                        "Failed to delete old image: "
                                                        + e.getMessage());
                }
        }
}