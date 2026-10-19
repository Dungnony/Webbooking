package com.Nhom2.booking.service;

import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploaded images in Supabase Storage when SUPABASE_URL and SUPABASE_KEY are set.
 * Without them it falls back to the local "uploads" folder, which is fine for development but is wiped
 * whenever the container is redeployed.
 */
@Service
public class ImageStorageService {

    // Uploads are served publicly by extension, so anything renderable as active content (html, svg) is refused.
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp",
            ".gif", "image/gif");

    private static final String LOCAL_URL_PREFIX = "/uploads/";
    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private final RestTemplate restTemplate;
    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucket;
    private final Path localDir;

    @Autowired
    public ImageStorageService(
            RestTemplate restTemplate,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.key:}") String serviceKey,
            @Value("${supabase.storage.bucket:uploads}") String bucket
    ) {
        this(restTemplate, supabaseUrl, serviceKey, bucket, Path.of("uploads"));
    }

    ImageStorageService(RestTemplate restTemplate, String supabaseUrl, String serviceKey, String bucket, Path localDir) {
        this.restTemplate = restTemplate;
        this.supabaseUrl = stripTrailingSlash(supabaseUrl);
        this.serviceKey = serviceKey;
        this.bucket = bucket;
        this.localDir = localDir;
        if (!usesSupabase()) {
            log.warn("SUPABASE_URL/SUPABASE_KEY not set: images are stored on local disk and are lost on redeploy");
        }
    }

    /** Saves the image under {@code folder} and returns the URL to store and render. */
    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("error.image.empty");
        }
        String extension = resolveImageExtension(file.getOriginalFilename());
        String objectPath = folder + "/" + UUID.randomUUID() + extension;
        byte[] bytes = readBytes(file);

        return usesSupabase()
                ? uploadToSupabase(objectPath, bytes, CONTENT_TYPES.get(extension))
                : saveLocally(objectPath, bytes);
    }

    /** Returns the lowercase extension with its dot if it is an allowed image format. */
    static String resolveImageExtension(String originalName) {
        int dotIndex = originalName == null ? -1 : originalName.lastIndexOf('.');
        String extension = dotIndex < 0 ? "" : originalName.substring(dotIndex).toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.containsKey(extension)) {
            throw new BusinessException("error.image.unsupportedType", String.join(", ", CONTENT_TYPES.keySet()));
        }
        return extension;
    }

    private String uploadToSupabase(String objectPath, byte[] bytes, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceKey);
        headers.set("apikey", serviceKey);
        // Derived from the validated extension, never from the browser, so a disguised file cannot be served as HTML.
        headers.setContentType(MediaType.parseMediaType(contentType));

        try {
            restTemplate.postForEntity(
                    supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath,
                    new HttpEntity<>(bytes, headers),
                    String.class);
        } catch (RestClientException e) {
            log.error("Supabase upload of {} failed", objectPath, e);
            throw new ServiceUnavailableException("error.image.uploadFailed", e);
        }
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    private String saveLocally(String objectPath, byte[] bytes) {
        try {
            Path target = localDir.resolve(objectPath);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new ServiceUnavailableException("error.image.uploadFailed", e);
        }
        return LOCAL_URL_PREFIX + objectPath;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("error.image.unreadable");
        }
    }

    private boolean usesSupabase() {
        return !supabaseUrl.isBlank() && serviceKey != null && !serviceKey.isBlank();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
