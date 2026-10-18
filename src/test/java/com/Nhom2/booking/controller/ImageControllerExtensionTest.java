package com.Nhom2.booking.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageControllerExtensionTest {

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "photo.JPEG", "a.b.png", "x.webp", "y.gif"})
    void acceptsCommonImageFormatsAndNormalisesCase(String name) {
        String extension = ImageController.resolveImageExtension(name);

        assertThat(extension).isEqualTo(extension.toLowerCase()).startsWith(".");
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.html", "evil.svg", "evil.jpg.html", "script.js", "noextension"})
    void rejectsFilesThatCouldBeServedAsActiveContent(String name) {
        assertThatThrownBy(() -> ImageController.resolveImageExtension(name))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingFileName(String name) {
        assertThatThrownBy(() -> ImageController.resolveImageExtension(name))
                .isInstanceOf(RuntimeException.class);
    }
}
