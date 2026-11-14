package com.jobportal.controller;

import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.Role;
import com.jobportal.service.AdminService;
import com.jobportal.service.ApplicationService;
import com.jobportal.service.JobService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final JobService jobService;
    private final ApplicationService applicationService;

    public AdminController(AdminService adminService, JobService jobService,
                           ApplicationService applicationService) {
        this.adminService = adminService;
        this.jobService = jobService;
        this.applicationService = applicationService;
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) Role role,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return adminService.users(role, page, size);
    }

    @PatchMapping("/users/{id}/enabled")
    public UserResponse setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        return adminService.setEnabled(id, value);
    }

    @DeleteMapping("/users/{id}")
    public MessageResponse deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return new MessageResponse("User deleted");
    }

    @GetMapping("/jobs")
    public PageResponse<JobResponse> jobs(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return jobService.adminList(page, size);
    }

    @DeleteMapping("/jobs/{id}")
    public MessageResponse deleteJob(@PathVariable Long id) {
        jobService.adminDelete(id);
        return new MessageResponse("Job deleted");
    }

    @GetMapping("/applications")
    public PageResponse<ApplicationResponse> applications(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return applicationService.adminList(page, size);
    }
}
