package com.trackai.backend.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.trackai.backend.dto.cloudinary.CloudinaryUploadResponse;
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

        private CloudinaryUploadResponse uploadMultipartFile(

                        MultipartFile file,

                        String folder) {

                try {

                        Map<?, ?> result =

                                        cloudinary.uploader().upload(

                                                        file.getBytes(),

                                                        ObjectUtils.asMap(

                                                                        "folder",

                                                                        folder,

                                                                        "public_id",

                                                                        UUID.randomUUID().toString(),

                                                                        "resource_type",

                                                                        "auto"

                                                        )

                                        );

                        return buildResponse(result);

                }

                catch (Exception e) {

                        throw new RuntimeException(

                                        "Cloudinary upload failed : "

                                                        + e.getMessage()

                        );

                }

        }

        private CloudinaryUploadResponse uploadBytes(

                        byte[] bytes,

                        String folder) {

                try {

                        Map<?, ?> result =

                                        cloudinary.uploader().upload(

                                                        bytes,

                                                        ObjectUtils.asMap(

                                                                        "folder",

                                                                        folder,

                                                                        "public_id",

                                                                        UUID.randomUUID().toString(),

                                                                        "resource_type",

                                                                        "image"

                                                        )

                                        );

                        return buildResponse(result);

                }

                catch (Exception e) {

                        throw new RuntimeException(

                                        "Cloudinary upload failed : "

                                                        + e.getMessage()

                        );

                }

        }

        private CloudinaryUploadResponse buildResponse(

                        Map<?, ?> result) {

                return CloudinaryUploadResponse

                                .builder()

                                .secureUrl(

                                                result.get("secure_url")

                                                                .toString()

                                )

                                .publicId(

                                                result.get("public_id")

                                                                .toString()

                                )

                                .width(

                                                ((Number)

                                                result.get("width"))

                                                                .intValue()

                                )

                                .height(

                                                ((Number)

                                                result.get("height"))

                                                                .intValue()

                                )

                                .format(

                                                result.get("format")

                                                                .toString()

                                )

                                .bytes(

                                                ((Number)

                                                result.get("bytes"))

                                                                .longValue()

                                )

                                .build();

        }

        @Override
        public CloudinaryUploadResponse uploadProfileImage(

                        MultipartFile file) {

                return uploadMultipartFile(

                                file,

                                "trackai/profile-images"

                );

        }

        @Override
        public CloudinaryUploadResponse uploadGeneratedImage(

                        byte[] imageBytes) {

                return uploadBytes(

                                imageBytes,

                                "trackai/generated-images"

                );

        }

        @Override
        public CloudinaryUploadResponse uploadResume(

                        MultipartFile file) {

                return uploadMultipartFile(

                                file,

                                "trackai/resumes"

                );

        }

        @Override
        public void deleteImage(
                        String publicId) {

                try {

                        if (publicId == null
                                        || publicId.isBlank()) {

                                return;

                        }

                        cloudinary.uploader()

                                        .destroy(

                                                        publicId,

                                                        ObjectUtils.emptyMap());

                } catch (Exception e) {

                        throw new RuntimeException(

                                        "Cloudinary delete failed : "

                                                        + e.getMessage());

                }

        }

}