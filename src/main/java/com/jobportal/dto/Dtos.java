package com.jobportal.dto;

import com.jobportal.entity.ApplicationStatus;
import com.jobportal.entity.JobType;
import com.jobportal.entity.Role;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

/** All request / response objects live here as compact records. */
public final class Dtos {

    private Dtos() {}

    // ---------- Auth ----------
    public record RegisterRequest(
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password,
            @NotNull Role role,
            String companyName) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, Long userId, String fullName, String email, Role role) {}

    // ---------- Users ----------
    public record UserResponse(Long id, String fullName, String email, Role role,
                               String companyName, boolean enabled, LocalDateTime createdAt) {}

    // ---------- Profile ----------
    public record ProfileRequest(String phone, String headline, String location, String education,
                                 @Min(0) Integer experienceYears, String skills, String summary) {}

    public record ProfileResponse(Long userId, String fullName, String email, String phone, String headline,
                                  String location, String education, Integer experienceYears,
                                  String skills, String summary, String resumeFileName) {}

    // ---------- Jobs ----------
    public record JobRequest(
            @NotBlank String title,
            @NotBlank String description,
            @NotBlank String location,
            @NotNull JobType jobType,
            String skillsRequired,
            @Min(0) Integer experienceRequired,
            @PositiveOrZero Double salaryMin,
            @PositiveOrZero Double salaryMax,
            Boolean active) {}

    public record JobResponse(Long id, String title, String description, String location, JobType jobType,
                              String skillsRequired, Integer experienceRequired, Double salaryMin,
                              Double salaryMax, boolean active, LocalDateTime postedAt,
                              Long recruiterId, String recruiterName, String companyName,
                              Integer matchScore) {}

    // ---------- Applications ----------
    public record ApplyRequest(@Size(max = 2000) String coverLetter) {}

    public record StatusUpdateRequest(@NotNull ApplicationStatus status) {}

    public record ApplicationResponse(Long id, Long jobId, String jobTitle, String companyName,
                                      Long applicantId, String applicantName, String applicantEmail,
                                      ApplicationStatus status, String coverLetter, LocalDateTime appliedAt) {}

    // ---------- Admin ----------
    public record StatsResponse(long jobSeekers, long recruiters, long totalJobs, long activeJobs,
                                long totalApplications, long applied, long shortlisted, long rejected) {}

    // ---------- Generic ----------
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record MessageResponse(String message) {}

    public record ApiError(LocalDateTime timestamp, int status, String message) {}
}
