package com.Nhom2.booking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Lưu ảnh upload.
 * - Có SUPABASE_KEY: lưu lên Supabase Storage (không mất khi deploy lại server).
 * - Không có: lưu vào thư mục uploads/ trên máy (chỉ dùng khi dev).
 */
@Service
public class StorageService {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Value("${supabase.bucket:images}")
    private String bucket;

    private static final String UPLOAD_DIR = "uploads";

    /**
     * Lưu ảnh vào thư mục con subDir (vd "hotels", "rooms"), trả về URL để hiển thị.
     */
    public String saveImage(MultipartFile file, String subDir) {
        if (file.isEmpty()) {
            throw new RuntimeException("File rỗng!");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Chỉ cho phép upload file ảnh!");
        }

        String fileName = subDir + "/" + UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        try {
            if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
                return saveLocal(file, fileName);
            }
            return uploadToSupabase(file.getBytes(), contentType, fileName);
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi lưu file: " + e.getMessage());
        }
    }

    private String uploadToSupabase(byte[] bytes, String contentType, String fileName) {
        String baseUrl = supabaseUrl.replaceAll("/+$", "");

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseKey);
        headers.setBearerAuth(supabaseKey);
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setCacheControl("max-age=31536000");

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.postForEntity(
                baseUrl + "/storage/v1/object/" + bucket + "/" + fileName,
                new HttpEntity<>(bytes, headers),
                String.class
        );

        // Bucket public -> ai cũng xem được ảnh qua link này
        return baseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName;
    }

    private String saveLocal(MultipartFile file, String fileName) throws IOException {
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // URL tương đối, được serve bởi WebConfig
        return "/" + UPLOAD_DIR + "/" + fileName;
    }

    private String extensionOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf("."))
                .toLowerCase()
                .replaceAll("[^a-z0-9.]", "");
    }
}
