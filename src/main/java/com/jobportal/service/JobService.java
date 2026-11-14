package com.jobportal.service;

import com.jobportal.dto.Dtos.JobRequest;
import com.jobportal.dto.Dtos.JobResponse;
import com.jobportal.dto.Dtos.PageResponse;
import com.jobportal.entity.*;
import com.jobportal.exception.BadRequestException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.ProfileRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobService {

    private static final Set<String> SORTABLE = Set.of("postedAt", "title", "salaryMax", "experienceRequired");

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ProfileRepository profileRepository;
    private final CurrentUserService currentUser;

    public JobService(JobRepository jobRepository, ApplicationRepository applicationRepository,
                      ProfileRepository profileRepository, CurrentUserService currentUser) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.profileRepository = profileRepository;
        this.currentUser = currentUser;
    }

    // ---------- public search ----------
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> search(String keyword, String location, JobType type, Integer maxExperience,
                                            int page, int size, String sortBy, String dir) {
        Specification<Job> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isTrue(root.get("active")));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("skillsRequired")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (location != null && !location.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("location")), "%" + location.trim().toLowerCase() + "%"));
            }
            if (type != null) ps.add(cb.equal(root.get("jobType"), type));
            if (maxExperience != null) {
                ps.add(cb.or(cb.isNull(root.get("experienceRequired")),
                        cb.lessThanOrEqualTo(root.get("experienceRequired"), maxExperience)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return toPage(jobRepository.findAll(spec, pageable(page, size, sortBy, dir)), null);
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return toResponse(find(id), null);
    }

    // ---------- recruiter ----------
    @Transactional
    public JobResponse create(String email, JobRequest req) {
        User recruiter = currentUser.get(email);
        validateSalary(req);
        Job job = new Job();
        job.setRecruiter(recruiter);
        apply(job, req);
        return toResponse(jobRepository.save(job), null);
    }

    @Transactional
    public JobResponse update(String email, Long id, JobRequest req) {
        Job job = find(id);
        ensureOwner(email, job);
        validateSalary(req);
        apply(job, req);
        return toResponse(jobRepository.save(job), null);
    }

    @Transactional
    public void delete(String email, Long id) {
        Job job = find(id);
        ensureOwner(email, job);
        applicationRepository.deleteByJobId(id);
        jobRepository.delete(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> myJobs(String email, int page, int size) {
        User recruiter = currentUser.get(email);
        return toPage(jobRepository.findByRecruiterId(recruiter.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postedAt"))), null);
    }

    // ---------- recommendations (skill matching against the seeker's profile) ----------
    @Transactional(readOnly = true)
    public List<JobResponse> recommend(String email) {
        User user = currentUser.get(email);
        Set<String> mine = tokens(profileRepository.findByUserId(user.getId())
                .map(JobSeekerProfile::getSkills).orElse(null));
        if (mine.isEmpty()) return List.of();

        return jobRepository.findByActiveTrue().stream()
                .map(j -> {
                    Set<String> req = tokens(j.getSkillsRequired());
                    if (req.isEmpty()) return Map.entry(j, 0);
                    long hit = req.stream().filter(mine::contains).count();
                    return Map.entry(j, (int) Math.round(100.0 * hit / req.size()));
                })
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> toResponse(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    // ---------- admin ----------
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> adminList(int page, int size) {
        return toPage(jobRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "postedAt"))), null);
    }

    @Transactional
    public void adminDelete(Long id) {
        Job job = find(id);
        applicationRepository.deleteByJobId(id);
        jobRepository.delete(job);
    }

    // ---------- helpers ----------
    private Job find(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id " + id));
    }

    private void ensureOwner(String email, Job job) {
        if (!job.getRecruiter().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Not your job posting");
        }
    }

    private void validateSalary(JobRequest r) {
        if (r.salaryMin() != null && r.salaryMax() != null && r.salaryMin() > r.salaryMax()) {
            throw new BadRequestException("Minimum salary cannot exceed maximum salary");
        }
    }

    private void apply(Job j, JobRequest r) {
        j.setTitle(r.title().trim());
        j.setDescription(r.description().trim());
        j.setLocation(r.location().trim());
        j.setJobType(r.jobType());
        j.setSkillsRequired(r.skillsRequired());
        j.setExperienceRequired(r.experienceRequired());
        j.setSalaryMin(r.salaryMin());
        j.setSalaryMax(r.salaryMax());
        if (r.active() != null) j.setActive(r.active());
    }

    private Set<String> tokens(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Pageable pageable(int page, int size, String sortBy, String dir) {
        String field = SORTABLE.contains(sortBy) ? sortBy : "postedAt";
        Sort.Direction d = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(d, field));
    }

    private PageResponse<JobResponse> toPage(Page<Job> p, Integer score) {
        return new PageResponse<>(p.getContent().stream().map(j -> toResponse(j, score)).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    public JobResponse toResponse(Job j, Integer score) {
        User r = j.getRecruiter();
        return new JobResponse(j.getId(), j.getTitle(), j.getDescription(), j.getLocation(), j.getJobType(),
                j.getSkillsRequired(), j.getExperienceRequired(), j.getSalaryMin(), j.getSalaryMax(),
                j.isActive(), j.getPostedAt(), r.getId(), r.getFullName(), r.getCompanyName(), score);
    }
}
