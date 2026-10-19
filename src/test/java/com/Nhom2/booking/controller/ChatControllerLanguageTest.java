package com.Nhom2.booking.controller;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerLanguageTest {

    @Test
    void englishUiAsksTheAssistantToReplyInEnglish() {
        assertThat(ChatController.replyLanguageRule(Locale.ENGLISH)).contains("in English");
        assertThat(ChatController.replyLanguageRule(Locale.US)).contains("in English");
    }

    @Test
    void vietnameseAndUnknownLanguagesKeepVietnameseReplies() {
        assertThat(ChatController.replyLanguageRule(Locale.forLanguageTag("vi"))).contains("tiếng Việt");
        assertThat(ChatController.replyLanguageRule(Locale.FRENCH)).contains("tiếng Việt");
    }
}
