package com.trackai.backend.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
import com.trackai.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

        private final Cloudinary cloudinary;

        // ==========================================================
        // Common upload logic for MultipartFile (profile image, resume, etc.)
        // ==========================================================
        private CloudinaryUploadResponse uploadMultipartFile(MultipartFile file, String folder) {
                try {
                        Map<?, ?> result = cloudinary.uploader().upload(
                                        file.getBytes(),
                                        ObjectUtils.asMap(
                                                        "folder", folder,
                                                        "public_id", UUID.randomUUID().toString(), // sirf UUID diya
                                                                                                   // hai, Cloudinary
                                                                                                   // khud folder prefix
                                                                                                   // add karke final
                                                                                                   // public_id return
                                                                                                   // karega
                                                        "resource_type", "auto"));
                        return buildResponse(result);
                } catch (Exception e) {
                        throw new RuntimeException("Cloudinary upload failed : " + e.getMessage());
                }
        }

        // ==========================================================
        // Common upload logic for raw bytes (e.g. AI-generated images)
        // ==========================================================
        private CloudinaryUploadResponse uploadBytes(byte[] bytes, String folder) {
                try {
                        Map<?, ?> result = cloudinary.uploader().upload(
                                        bytes,
                                        ObjectUtils.asMap(
                                                        "folder", folder,
                                                        "public_id", UUID.randomUUID().toString(),
                                                        "resource_type", "image"));
                        return buildResponse(result);
                } catch (Exception e) {
                        throw new RuntimeException("Cloudinary upload failed : " + e.getMessage());
                }
        }

        // ==========================================================
        // Cloudinary response ko apne DTO mein map karna
        // NOTE: result.get("public_id") mein already "folder/uuid" format
        // ka full public_id hota hai — ye wahi cheez hai jo hume
        // DELETE karte waqt chahiye hoti hai, URL nahi.
        // ==========================================================
        private CloudinaryUploadResponse buildResponse(Map<?, ?> result) {
                return CloudinaryUploadResponse.builder()
                                .secureUrl(result.get("secure_url").toString()) // display/UI ke liye
                                .publicId(result.get("public_id").toString()) // delete/update ke liye - ISE HI DB MEIN
                                                                              // SAVE KARO
                                .width(((Number) result.get("width")).intValue())
                                .height(((Number) result.get("height")).intValue())
                                .format(result.get("format").toString())
                                .bytes(((Number) result.get("bytes")).longValue())
                                .build();
        }

        @Override
        public CloudinaryUploadResponse uploadProfileImage(MultipartFile file) {
                return uploadMultipartFile(file, "trackai/profile-images");
        }

        @Override
        public CloudinaryUploadResponse uploadGeneratedImage(byte[] imageBytes) {
                return uploadBytes(imageBytes, "trackai/generated-images");
        }

        @Override
        public CloudinaryUploadResponse uploadResume(MultipartFile file) {
                return uploadMultipartFile(file, "trackai/resumes");
        }

        // ==========================================================
        // DELETE - isko hamesha "publicId" milna chahiye, URL nahi!
        // ==========================================================
        @Override
        public void deleteImage(String publicId) {

                // agar publicId hi nahi hai toh delete karne ka koi matlab nahi
                if (publicId == null || publicId.isBlank()) {
                        log.warn("deleteImage called with null/blank publicId, skipping");
                        return;
                }

                try {
                        // Cloudinary ka destroy() method result return karta hai:
                        // { "result": "ok" } -> successfully delete hua
                        // { "result": "not found" } -> ye id Cloudinary pe exist hi nahi karti
                        Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

                        String status = result != null ? String.valueOf(result.get("result")) : "unknown";

                        log.info("Cloudinary delete for publicId [{}] -> result: {}", publicId, status);

                        // PEHLE ye check missing tha, isliye "not found" wala silent fail dikh raha tha
                        if (!"ok".equals(status)) {
                                log.warn("Cloudinary delete FAILED for publicId [{}]. Full response: {}", publicId,
                                                result);
                        }

                } catch (Exception e) {
                        throw new RuntimeException("Cloudinary delete failed : " + e.getMessage());
                }
        }
}