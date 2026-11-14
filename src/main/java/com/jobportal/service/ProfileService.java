package com.jobportal.service;

import com.jobportal.dto.Dtos.ProfileRequest;
import com.jobportal.dto.Dtos.ProfileResponse;
import com.jobportal.entity.JobSeekerProfile;
import com.jobportal.entity.User;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.ProfileRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final CurrentUserService currentUser;
    private final FileStorageService storage;

    public ProfileService(ProfileRepository profileRepository, CurrentUserService currentUser,
                          FileStorageService storage) {
        this.profileRepository = profileRepository;
        this.currentUser = currentUser;
        this.storage = storage;
    }

    @Transactional
    public ProfileResponse get(String email) {
        return toResponse(findOrCreate(currentUser.get(email)));
    }

    @Transactional
    public ProfileResponse update(String email, ProfileRequest req) {
        JobSeekerProfile p = findOrCreate(currentUser.get(email));
        p.setPhone(req.phone());
        p.setHeadline(req.headline());
        p.setLocation(req.location());
        p.setEducation(req.education());
        p.setExperienceYears(req.experienceYears());
        p.setSkills(req.skills());
        p.setSummary(req.summary());
        return toResponse(profileRepository.save(p));
    }

    @Transactional
    public ProfileResponse uploadResume(String email, MultipartFile file) {
        User user = currentUser.get(email);
        JobSeekerProfile p = findOrCreate(user);
        String stored = storage.storeResume(file, user.getId());
        storage.delete(p.getResumePath());
        p.setResumePath(stored);
        p.setResumeOriginalName(file.getOriginalFilename());
        return toResponse(profileRepository.save(p));
    }

    /** Used by recruiters / admin to download an applicant's resume. */
    @Transactional(readOnly = true)
    public Resource loadResume(Long applicantUserId) {
        JobSeekerProfile p = profileRepository.findByUserId(applicantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        if (p.getResumePath() == null) {
            throw new ResourceNotFoundException("Applicant has not uploaded a resume");
        }
        return storage.load(p.getResumePath());
    }

    @Transactional(readOnly = true)
    public String resumeFileName(Long applicantUserId) {
        return profileRepository.findByUserId(applicantUserId)
                .map(p -> p.getResumeOriginalName() != null ? p.getResumeOriginalName() : p.getResumePath())
                .orElse("resume");
    }

    private JobSeekerProfile findOrCreate(User user) {
        return profileRepository.findByUserId(user.getId())
                .orElseGet(() -> profileRepository.save(new JobSeekerProfile(user)));
    }

    private ProfileResponse toResponse(JobSeekerProfile p) {
        User u = p.getUser();
        return new ProfileResponse(u.getId(), u.getFullName(), u.getEmail(), p.getPhone(), p.getHeadline(),
                p.getLocation(), p.getEducation(), p.getExperienceYears(), p.getSkills(), p.getSummary(),
                p.getResumeOriginalName());
    }
}
