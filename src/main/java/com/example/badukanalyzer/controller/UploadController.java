package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.UploadResponse;
import com.example.badukanalyzer.service.GibService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
public class UploadController {

    private final GibService gibService;

    public UploadController(GibService gibService) {
        this.gibService = gibService;
    }

    @PostMapping("/upload/gib")
    public ResponseEntity<UploadResponse> uploadGib(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".gib")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path tempFile = Files.createTempFile("upload-", ".gib");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            String sgfContent = gibService.convertGibToSgf(tempFile.toString());

            Files.deleteIfExists(tempFile);

            UploadResponse response = UploadResponse.builder()
                    .filename(originalFilename)
                    .sgfContent(sgfContent)
                    .build();

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
