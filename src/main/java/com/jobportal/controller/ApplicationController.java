package com.jobportal.controller;

import com.jobportal.dto.Dtos.*;
import com.jobportal.service.ApplicationService;
import com.jobportal.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final ProfileService profileService;

    public ApplicationController(ApplicationService applicationService, ProfileService profileService) {
        this.applicationService = applicationService;
        this.profileService = profileService;
    }

    @PostMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public ResponseEntity<ApplicationResponse> apply(Authentication auth, @PathVariable Long jobId,
                                                     @Valid @RequestBody(required = false) ApplyRequest req) {
        String cover = req == null ? null : req.coverLetter();
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(auth.getName(), jobId, cover));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public PageResponse<ApplicationResponse> mine(Authentication auth,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return applicationService.myApplications(auth.getName(), page, size);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ApplicationResponse updateStatus(Authentication auth, @PathVariable Long id,
                                            @Valid @RequestBody StatusUpdateRequest req) {
        return applicationService.updateStatus(auth.getName(), id, req.status());
    }

    @GetMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<Resource> resume(Authentication auth, @PathVariable Long id) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Long applicantId = applicationService.applicantIdForResume(auth.getName(), admin, id);
        Resource file = profileService.loadResume(applicantId);
        String name = profileService.resumeFileName(applicantId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name.replace("\"", "") + "\"")
                .body(file);
    }
}
