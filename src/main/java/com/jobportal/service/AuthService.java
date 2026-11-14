package com.jobportal.service;

import com.jobportal.dto.Dtos.*;
import com.jobportal.entity.JobSeekerProfile;
import com.jobportal.entity.Role;
import com.jobportal.entity.User;
import com.jobportal.exception.BadRequestException;
import com.jobportal.exception.ConflictException;
import com.jobportal.repository.ProfileRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository, ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailService emailService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot be self-registered");
        }
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (req.role() == Role.RECRUITER && (req.companyName() == null || req.companyName().isBlank())) {
            throw new BadRequestException("Company name is required for recruiters");
        }
        User newUser = new User();
        newUser.setFullName(req.fullName().trim());
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(req.password()));
        newUser.setRole(req.role());
        newUser.setCompanyName(req.role() == Role.RECRUITER ? req.companyName().trim() : null);
        User user = userRepository.save(newUser);

        if (user.getRole() == Role.JOB_SEEKER) {
            profileRepository.save(new JobSeekerProfile(user));
        }
        emailService.send(email, "Welcome to Job Portal",
                "Hi " + user.getFullName() + ",\n\nYour account has been created successfully.");
        return toAuth(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BadRequestException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new BadRequestException("Your account has been disabled. Contact the administrator.");
        }
        return toAuth(user);
    }

    private AuthResponse toAuth(User u) {
        return new AuthResponse(jwtUtil.generateToken(u.getEmail(), u.getRole().name()),
                u.getId(), u.getFullName(), u.getEmail(), u.getRole());
    }
}
