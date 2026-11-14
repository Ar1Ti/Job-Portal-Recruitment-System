package com.jobportal.repository;

import com.jobportal.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    @Override
    @EntityGraph(attributePaths = "recruiter")
    Page<Job> findAll(org.springframework.data.jpa.domain.Specification<Job> spec, Pageable pageable);

    @EntityGraph(attributePaths = "recruiter")
    Page<Job> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "recruiter")
    Optional<Job> findById(Long id);

    @EntityGraph(attributePaths = "recruiter")
    Page<Job> findByRecruiterId(Long recruiterId, Pageable pageable);

    @EntityGraph(attributePaths = "recruiter")
    List<Job> findByActiveTrue();

    long countByActiveTrue();
}
