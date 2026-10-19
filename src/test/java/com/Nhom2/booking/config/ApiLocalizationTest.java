package com.Nhom2.booking.config;

import com.Nhom2.booking.controller.AuthController;
import com.Nhom2.booking.dto.LoginRequest;
import com.Nhom2.booking.exception.UnauthorizedException;
import com.Nhom2.booking.i18n.Messages;
import com.Nhom2.booking.security.JwtUtil;
import com.Nhom2.booking.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End to end through the real LocaleResolver: the Accept-Language header picks the error language. */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtUtil.class, LocaleConfig.class, Messages.class})
class ApiLocalizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @BeforeEach
    void wrongPassword() {
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("error.login.invalidCredentials"));
    }

    @Test
    void englishRequestGetsAnEnglishErrorWithAStableCode() throws Exception {
        mockMvc.perform(login().header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong username or password."))
                .andExpect(jsonPath("$.code").value("error.login.invalidCredentials"));
    }

    @Test
    void requestWithoutALanguageGetsVietnamese() throws Exception {
        mockMvc.perform(login())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không đúng."));
    }

    private static MockHttpServletRequestBuilder login() {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong\"}");
    }
}
