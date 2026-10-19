package com.Nhom2.booking.controller;

import com.Nhom2.booking.entity.AppConfig;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.repository.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // Clients may only replay the conversation; the system prompt is always built server-side.
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");
    static final int MAX_MESSAGES = 20;
    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_HOTEL_FIELD_LENGTH = 500;

    private final AppConfigRepository configRepository;
    private final RestTemplate restTemplate;
    private final Messages messages;

    public ChatController(AppConfigRepository configRepository, RestTemplate restTemplate, Messages messages) {
        this.configRepository = configRepository;
        this.restTemplate = restTemplate;
        this.messages = messages;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> chatWithAI(@RequestBody ChatRequest request) {
        List<Map<String, String>> conversation = sanitizeMessages(request.getMessages());
        if (conversation.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("reply", messages.get("error.ai.invalidMessage")));
        }

        try {
            AppConfig apiKeyConfig = configRepository.findById("OPENAI_API_KEY")
                    .orElseThrow(() -> new IllegalStateException("OPENAI_API_KEY not found"));
            String openAiApiKey = apiKeyConfig.getConfigValue();

            Map<String, Object> hotelInfo = request.getHotelInfo() == null ? Map.of() : request.getHotelInfo();
            String systemPrompt = String.format(
                """
                Bạn là Trợ lý AI Booking của hệ thống đặt phòng khách sạn Nhóm 2.
                Bạn đang hỗ trợ khách tại khách sạn: "%s" (%s) - Rating: %s/10.
                Mô tả: %s

                Bạn có thông tin đầy đủ về TẤT CẢ khách sạn trong hệ thống:

                1. Lucien Hanoi Lakeside Rooftop & Bar (4.8★): Classic Double 1.2tr | Junior 1.4tr | Junior City View 1.6tr | Executive 2.2tr | Executive Balcony 2.6tr | Signature Suite 3.8tr | Family Suite 4.2-5tr
                2. Solare De Monte Hotel & Spa (4.7★): Junior 1.4tr | Deluxe 1.8tr | Junior Suite 2tr | Premier 2.4tr | Connecting 2.8tr | Family 3.2tr
                3. Hanoi Emerald Waters Hotel & Spa (4.6★): Junior 1.3tr | Deluxe 1.6tr | Deluxe Family 2.4tr | Family City View 3tr
                4. Hotel Emerald Waters Classy (4.5★): Superior 1.4tr | Deluxe 1.8tr | Connecting 2.6tr | Suite Balcony 3.5tr | Family Suite 4.5tr
                5. Hanoi Emerald Waters Valley (4.5★): Deluxe 1.5tr | Executive 2tr | Family Suite 3.8tr
                6. Hanoi Dalvostro Valentino (4.7★): Classic 1.3tr | Deluxe 1.9tr | Junior Suite 2.4tr | Connecting 2.8tr | Suite 3.2tr | Valentino Suite 5.5tr
                7. San Premium Hotel (4.6★): Deluxe 1.7tr | Junior City View 2.1tr | Premium Balcony 2.8tr | Family 4tr
                8. H Hotel L Art Hanoi (4.8★): Junior 1.5tr | Deluxe 2tr | Executive Balcony 2.6tr | Executive City View 2.8tr | Privilege 3.2tr | Family 4tr | L'Art Signature 6tr
                9. La Belle Maison (4.7★): Suite Balcony Double/Twin 3.5tr | Executive Suite Lake View 5tr
                10. San Palace Hotel (4.6★): Deluxe 1.9tr | Premium City View 2.4tr | Executive Balcony 3tr | Family Suite 4.2tr
                11. San Boutique Hotel (4.5★): Deluxe 1.6tr | Premium Balcony 2.2tr | Executive Balcony 2.8tr | Family 4tr
                12. Old Quarter Hotel (4.4★): Standard Double 1.1tr | Executive Triple 2tr | King Lake View 2.6tr | Family Suite 3.8tr | Suite Balcony 4.5tr
                13. Casa Valentina Hotel (4.6★): Deluxe 1.8tr | Suite City View 3.2tr | Suite Balcony 3.8tr | Family Suite 5tr

                PHÒNG RẺ NHẤT hệ thống: Standard Double tại Old Quarter Hotel - 1.100.000đ/đêm
                PHÒNG ĐẮT NHẤT: L'Art Signature tại H Hotel L Art Hanoi - 6.000.000đ/đêm
                TẤT CẢ khách sạn đều ở Hà Nội.

                Quy tắc:
                - %s
                - Khi tư vấn hãy hỏi thêm: số người, ngân sách, có trẻ em không
                - KHÔNG được nói "không có thông tin" vì bạn đã có đủ dữ liệu ở trên
                - Giá hiển thị dạng: 1.200.000đ/đêm
                """,
                hotelField(hotelInfo, "name"),
                hotelField(hotelInfo, "city"),
                hotelField(hotelInfo, "ratingAvg"),
                hotelField(hotelInfo, "description"),
                replyLanguageRule(LocaleContextHolder.getLocale())
            );

            List<Map<String, String>> openAiMessages = new ArrayList<>();
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            openAiMessages.add(systemMessage);
            openAiMessages.addAll(conversation);

            Map<String, Object> openAiRequestBody = new HashMap<>();
            openAiRequestBody.put("model", "gpt-3.5-turbo");
            openAiRequestBody.put("messages", openAiMessages);
            openAiRequestBody.put("max_tokens", 500);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(openAiRequestBody, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.openai.com/v1/chat/completions", entity, Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

            Map<String, String> result = new HashMap<>();
            result.put("reply", (String) message.get("content"));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Chat request to OpenAI failed", e);
            return ResponseEntity.status(500).body(Map.of("reply", messages.get("error.ai.unavailable")));
        }
    }

    /**
     * Keeps only user/assistant turns with non-blank content, the most recent {@link #MAX_MESSAGES},
     * each truncated to {@link #MAX_MESSAGE_LENGTH} characters.
     */
    static List<Map<String, String>> sanitizeMessages(List<Map<String, String>> messages) {
        if (messages == null) {
            return List.of();
        }

        List<Map<String, String>> valid = messages.stream()
                .filter(m -> m != null && ALLOWED_ROLES.contains(m.get("role")))
                .filter(m -> m.get("content") != null && !m.get("content").isBlank())
                .map(m -> Map.of("role", m.get("role"), "content", truncate(m.get("content"), MAX_MESSAGE_LENGTH)))
                .toList();

        return valid.subList(Math.max(0, valid.size() - MAX_MESSAGES), valid.size());
    }

    /**
     * The prompt itself is Vietnamese, so without an explicit rule the model answers in Vietnamese
     * even when the guest uses the English UI.
     */
    static String replyLanguageRule(Locale locale) {
        if (Locale.ENGLISH.getLanguage().equals(locale.getLanguage())) {
            return "Always reply briefly and warmly in English (prices may stay in VND), "
                    + "unless the guest writes in another language; then reply in that language";
        }
        return "Trả lời ngắn gọn, thân thiện bằng tiếng Việt, trừ khi khách viết bằng ngôn ngữ khác thì trả lời bằng ngôn ngữ đó";
    }

    private static String hotelField(Map<String, Object> hotelInfo, String key) {
        Object value = hotelInfo.get(key);
        return value == null ? "" : truncate(String.valueOf(value), MAX_HOTEL_FIELD_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public static class ChatRequest {
        private List<Map<String, String>> messages;
        private Map<String, Object> hotelInfo;

        public List<Map<String, String>> getMessages() { return messages; }
        public void setMessages(List<Map<String, String>> messages) { this.messages = messages; }
        public Map<String, Object> getHotelInfo() { return hotelInfo; }
        public void setHotelInfo(Map<String, Object> hotelInfo) { this.hotelInfo = hotelInfo; }
    }
}
