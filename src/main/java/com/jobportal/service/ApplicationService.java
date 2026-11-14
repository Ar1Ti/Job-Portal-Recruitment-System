package com.jobportal.service;

import com.jobportal.dto.Dtos.ApplicationResponse;
import com.jobportal.dto.Dtos.PageResponse;
import com.jobportal.entity.*;
import com.jobportal.exception.BadRequestException;
import com.jobportal.exception.ConflictException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CurrentUserService currentUser;
    private final EmailService emailService;

    public ApplicationService(ApplicationRepository applicationRepository, JobRepository jobRepository,
                              CurrentUserService currentUser, EmailService emailService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.currentUser = currentUser;
        this.emailService = emailService;
    }

    @Transactional
    public ApplicationResponse apply(String email, Long jobId, String coverLetter) {
        User seeker = currentUser.get(email);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id " + jobId));
        if (!job.isActive()) {
            throw new BadRequestException("This job is no longer accepting applications");
        }
        if (applicationRepository.existsByJobIdAndApplicantId(jobId, seeker.getId())) {
            throw new ConflictException("You have already applied for this job");
        }
        Application app = applicationRepository.save(new Application(job, seeker, coverLetter));

        emailService.send(seeker.getEmail(), "Application received: " + job.getTitle(),
                "Hi " + seeker.getFullName() + ",\n\nYour application for \"" + job.getTitle() + "\" at "
                        + job.getRecruiter().getCompanyName() + " has been received.");
        emailService.send(job.getRecruiter().getEmail(), "New applicant for " + job.getTitle(),
                seeker.getFullName() + " (" + seeker.getEmail() + ") applied for your job \"" + job.getTitle() + "\".");
        return toResponse(app);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> myApplications(String email, int page, int size) {
        User seeker = currentUser.get(email);
        return toPage(applicationRepository.findByApplicantId(seeker.getId(), pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> applicantsForJob(String email, Long jobId, int page, int size) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id " + jobId));
        if (!job.getRecruiter().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Not your job posting");
        }
        return toPage(applicationRepository.findByJobId(jobId, pageable(page, size)));
    }

    @Transactional
    public ApplicationResponse updateStatus(String email, Long applicationId, ApplicationStatus status) {
        Application app = find(applicationId);
        if (!app.getJob().getRecruiter().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Not your job posting");
        }
        if (status == ApplicationStatus.APPLIED) {
            throw new BadRequestException("Status can only be changed to SHORTLISTED or REJECTED");
        }
        app.setStatus(status);
        applicationRepository.save(app);

        emailService.send(app.getApplicant().getEmail(),
                "Update on your application: " + app.getJob().getTitle(),
                "Hi " + app.getApplicant().getFullName() + ",\n\nYour application for \""
                        + app.getJob().getTitle() + "\" is now: " + status + ".");
        return toResponse(app);
    }

    /** Returns the applicant's user id if the caller (recruiter owner or admin) may view the resume. */
    @Transactional(readOnly = true)
    public Long applicantIdForResume(String email, boolean isAdmin, Long applicationId) {
        Application app = find(applicationId);
        if (!isAdmin && !app.getJob().getRecruiter().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Not your job posting");
        }
        return app.getApplicant().getId();
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> adminList(int page, int size) {
        return toPage(applicationRepository.findAll(pageable(page, size)));
    }

    private Application find(Long id) {
        return applicationRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id " + id));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "appliedAt"));
    }

    private PageResponse<ApplicationResponse> toPage(Page<Application> p) {
        return new PageResponse<>(p.getContent().stream().map(this::toResponse).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    private ApplicationResponse toResponse(Application a) {
        return new ApplicationResponse(a.getId(), a.getJob().getId(), a.getJob().getTitle(),
                a.getJob().getRecruiter().getCompanyName(), a.getApplicant().getId(),
                a.getApplicant().getFullName(), a.getApplicant().getEmail(),
                a.getStatus(), a.getCoverLetter(), a.getAppliedAt());
    }
}
