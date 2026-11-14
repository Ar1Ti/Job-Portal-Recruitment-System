package com.jobportal.repository;

import com.jobportal.entity.Application;
import com.jobportal.entity.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByJobIdAndApplicantId(Long jobId, Long applicantId);

    @EntityGraph(attributePaths = {"job", "job.recruiter", "applicant"})
    Page<Application> findByApplicantId(Long applicantId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.recruiter", "applicant"})
    Page<Application> findByJobId(Long jobId, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.recruiter", "applicant"})
    Optional<Application> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"job", "job.recruiter", "applicant"})
    Page<Application> findAll(Pageable pageable);

    long countByStatus(ApplicationStatus status);

    void deleteByJobId(Long jobId);

    void deleteByApplicantId(Long applicantId);
}
