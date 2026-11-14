package com.jobportal.service;

import com.jobportal.dto.Dtos.PageResponse;
import com.jobportal.dto.Dtos.StatsResponse;
import com.jobportal.dto.Dtos.UserResponse;
import com.jobportal.entity.ApplicationStatus;
import com.jobportal.entity.Job;
import com.jobportal.entity.Role;
import com.jobportal.entity.User;
import com.jobportal.exception.BadRequestException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.ProfileRepository;
import com.jobportal.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ProfileRepository profileRepository;
    private final FileStorageService storage;

    public AdminService(UserRepository userRepository, JobRepository jobRepository,
                        ApplicationRepository applicationRepository, ProfileRepository profileRepository,
                        FileStorageService storage) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> users(Role role, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> p = role == null ? userRepository.findAll(pr) : userRepository.findByRole(role, pr);
        return new PageResponse<>(p.getContent().stream().map(this::toResponse).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        User u = find(id);
        if (u.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be disabled");
        }
        u.setEnabled(enabled);
        return toResponse(userRepository.save(u));
    }

    @Transactional
    public void deleteUser(Long id) {
        User u = find(id);
        if (u.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be deleted");
        }
        if (u.getRole() == Role.RECRUITER) {
            // remove their jobs (and applications to those jobs) first
            for (Job j : jobRepository.findByRecruiterId(u.getId(), Pageable.unpaged())) {
                applicationRepository.deleteByJobId(j.getId());
                jobRepository.delete(j);
            }
        } else if (u.getRole() == Role.JOB_SEEKER) {
            applicationRepository.deleteByApplicantId(u.getId());
            profileRepository.findByUserId(u.getId()).ifPresent(p -> {
                storage.delete(p.getResumePath());
                profileRepository.delete(p);
            });
        }
        userRepository.delete(u);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        return new StatsResponse(
                userRepository.countByRole(Role.JOB_SEEKER),
                userRepository.countByRole(Role.RECRUITER),
                jobRepository.count(),
                jobRepository.countByActiveTrue(),
                applicationRepository.count(),
                applicationRepository.countByStatus(ApplicationStatus.APPLIED),
                applicationRepository.countByStatus(ApplicationStatus.SHORTLISTED),
                applicationRepository.countByStatus(ApplicationStatus.REJECTED));
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(),
                u.getCompanyName(), u.isEnabled(), u.getCreatedAt());
    }
}
