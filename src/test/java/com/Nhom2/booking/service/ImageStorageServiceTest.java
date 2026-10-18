package com.Nhom2.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ImageStorageServiceTest {

    private static final String SUPABASE_URL = "https://project.supabase.co";
    private static final String SERVICE_KEY = "service-role-key";
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G'};

    @TempDir
    Path tempDir;

    // ── extension whitelist ────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "photo.JPEG", "a.b.png", "x.webp", "y.gif"})
    void acceptsCommonImageFormatsAndNormalisesCase(String name) {
        String extension = ImageStorageService.resolveImageExtension(name);

        assertThat(extension).isEqualTo(extension.toLowerCase()).startsWith(".");
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.html", "evil.svg", "evil.jpg.html", "script.js", "noextension"})
    void rejectsFilesThatCouldBeServedAsActiveContent(String name) {
        assertThatThrownBy(() -> ImageStorageService.resolveImageExtension(name))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingFileName(String name) {
        assertThatThrownBy(() -> ImageStorageService.resolveImageExtension(name))
                .isInstanceOf(RuntimeException.class);
    }

    // ── Supabase Storage ───────────────────────────────────────────────────────

    @Test
    void uploadsToSupabaseWithAContentTypeDerivedFromTheExtensionAndReturnsThePublicUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getURI().toString())
                        .startsWith(SUPABASE_URL + "/storage/v1/object/uploads/hotels/")
                        .endsWith(".png"))
                .andExpect(header("Authorization", "Bearer " + SERVICE_KEY))
                .andExpect(header("apikey", SERVICE_KEY))
                // The browser-supplied type is ignored so a disguised file cannot be served as HTML.
                .andExpect(header("Content-Type", "image/png"))
                .andRespond(withSuccess("{\"Key\":\"uploads/hotels/x.png\"}", MediaType.APPLICATION_JSON));

        ImageStorageService storage = new ImageStorageService(restTemplate, SUPABASE_URL + "/", SERVICE_KEY, "uploads", tempDir);
        String url = storage.store(new MockMultipartFile("file", "photo.PNG", "text/html", PNG_BYTES), "hotels");

        server.verify();
        assertThat(url).startsWith(SUPABASE_URL + "/storage/v1/object/public/uploads/hotels/").endsWith(".png");
    }

    @Test
    void supabaseFailureIsReportedNotSwallowed() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(SUPABASE_URL))).andRespond(withServerError());

        ImageStorageService storage = new ImageStorageService(restTemplate, SUPABASE_URL, SERVICE_KEY, "uploads", tempDir);

        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "a.jpg", "image/jpeg", PNG_BYTES), "rooms"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── local fallback ─────────────────────────────────────────────────────────

    @Test
    void fallsBackToLocalDiskWhenSupabaseIsNotConfigured() throws Exception {
        ImageStorageService storage = new ImageStorageService(new RestTemplate(), "", "", "uploads", tempDir);

        String url = storage.store(new MockMultipartFile("file", "a.webp", "image/webp", PNG_BYTES), "avatars");

        assertThat(url).startsWith("/uploads/avatars/").endsWith(".webp");
        Path saved = tempDir.resolve(url.substring("/uploads/".length()));
        assertThat(Files.readAllBytes(saved)).isEqualTo(PNG_BYTES);
    }

    @Test
    void emptyFileIsRejected() {
        ImageStorageService storage = new ImageStorageService(new RestTemplate(), "", "", "uploads", tempDir);

        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "a.png", "image/png", new byte[0]), "hotels"))
                .isInstanceOf(RuntimeException.class);
    }
}
