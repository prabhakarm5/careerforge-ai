package com.trackai.backend.service;

import com.trackai.backend.entity.CoverLetterProject;
import com.trackai.backend.entity.ResumeProject;

public interface CoverLetterDocumentService {
    byte[] createPdf(CoverLetterProject project, ResumeProject resumeProject);
    byte[] createDocx(CoverLetterProject project, ResumeProject resumeProject);
}
