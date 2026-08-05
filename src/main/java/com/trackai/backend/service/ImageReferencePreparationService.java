package com.trackai.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageReferencePreparationService {

    MultipartFile prepare(MultipartFile file);
}
