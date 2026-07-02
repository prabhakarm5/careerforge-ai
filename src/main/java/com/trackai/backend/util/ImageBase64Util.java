package com.trackai.backend.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

public final class ImageBase64Util {

    private ImageBase64Util() {
    }

    public static String encode(MultipartFile file) {

        try {

            return Base64.getEncoder()

                    .encodeToString(

                            file.getBytes()

                    );

        }

        catch (IOException e) {

            throw new RuntimeException(

                    "Unable to encode image.",

                    e

            );

        }

    }

}