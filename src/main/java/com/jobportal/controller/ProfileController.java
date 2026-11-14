package com.jobportal.controller;

import com.jobportal.dto.Dtos.ProfileRequest;
import com.jobportal.dto.Dtos.ProfileResponse;
import com.jobportal.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@PreAuthorize("hasRole('JOB_SEEKER')")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(Authentication auth) {
        return profileService.get(auth.getName());
    }

    @PutMapping
    public ProfileResponse update(Authentication auth, @Valid @RequestBody ProfileRequest req) {
        return profileService.update(auth.getName(), req);
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadResume(Authentication auth, @RequestParam("file") MultipartFile file) {
        return profileService.uploadResume(auth.getName(), file);
    }
}
