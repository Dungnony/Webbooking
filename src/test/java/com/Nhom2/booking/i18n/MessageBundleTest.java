package com.Nhom2.booking.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against an error code reaching users untranslated in either language. */
class MessageBundleTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final Pattern CODE_LITERAL = Pattern.compile("\"(error\\.[A-Za-z0-9_.]+)\"");

    @Test
    void vietnameseAndEnglishDefineTheSameKeys() throws IOException {
        assertThat(load("messages_en.properties").stringPropertyNames())
                .containsExactlyInAnyOrderElementsOf(load("messages.properties").stringPropertyNames());
    }

    @Test
    void everyErrorCodeUsedInTheCodeHasATranslation() throws IOException {
        Set<String> codes = codesUsedInMainSources();
        assertThat(codes).isNotEmpty();

        assertThat(load("messages.properties").stringPropertyNames()).containsAll(codes);
        assertThat(load("messages_en.properties").stringPropertyNames()).containsAll(codes);
    }

    private static Set<String> codesUsedInMainSources() throws IOException {
        Set<String> codes = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = CODE_LITERAL.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    codes.add(matcher.group(1));
                }
            }
        }
        return codes;
    }

    private static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
