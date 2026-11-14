package com.jobportal.service;

import com.jobportal.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx");

    private final Path root;

    public FileStorageService(@Value("${app.upload.dir}") String dir) throws IOException {
        this.root = Paths.get(dir, "resumes").toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** Saves the file and returns the stored (generated) file name. */
    public String storeResume(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose a file to upload");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new BadRequestException("Only PDF, DOC and DOCX resumes are allowed");
        }
        String stored = userId + "_" + UUID.randomUUID() + "." + ext;
        try {
            Files.copy(file.getInputStream(), root.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
        return stored;
    }

    public Resource load(String storedName) {
        try {
            Path p = root.resolve(storedName).normalize();
            if (!p.startsWith(root) || !Files.exists(p)) {
                throw new BadRequestException("Resume file not found");
            }
            return new UrlResource(p.toUri());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(String storedName) {
        if (storedName == null) return;
        try {
            Files.deleteIfExists(root.resolve(storedName).normalize());
        } catch (IOException ignored) {
        }
    }
}
