package com.jobportal.controller;

import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.JobType;
import com.jobportal.service.ApplicationService;
import com.jobportal.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final ApplicationService applicationService;

    public JobController(JobService jobService, ApplicationService applicationService) {
        this.jobService = jobService;
        this.applicationService = applicationService;
    }

    // ----- public -----
    @GetMapping
    public PageResponse<JobResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) JobType jobType,
            @RequestParam(required = false) Integer maxExperience,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "postedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String dir) {
        return jobService.search(keyword, location, jobType, maxExperience, page, size, sortBy, dir);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable Long id) {
        return jobService.get(id);
    }

    // ----- job seeker -----
    @GetMapping("/recommendations")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public List<JobResponse> recommendations(Authentication auth) {
        return jobService.recommend(auth.getName());
    }

    // ----- recruiter -----
    @GetMapping("/my")
    @PreAuthorize("hasRole('RECRUITER')")
    public PageResponse<JobResponse> myJobs(Authentication auth,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return jobService.myJobs(auth.getName(), page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponse> create(Authentication auth, @Valid @RequestBody JobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.create(auth.getName(), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody JobRequest req) {
        return jobService.update(auth.getName(), id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public MessageResponse delete(Authentication auth, @PathVariable Long id) {
        jobService.delete(auth.getName(), id);
        return new MessageResponse("Job deleted");
    }

    @GetMapping("/{id}/applicants")
    @PreAuthorize("hasRole('RECRUITER')")
    public PageResponse<ApplicationResponse> applicants(Authentication auth, @PathVariable Long id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return applicationService.applicantsForJob(auth.getName(), id, page, size);
    }
}
