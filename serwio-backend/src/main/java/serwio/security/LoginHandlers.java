package serwio.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class LoginHandlers {
    private final ObjectMapper mapper;
    private final LoginStatusController loginStatus;
    private final SimpleUrlAuthenticationSuccessHandler success =
            new SimpleUrlAuthenticationSuccessHandler("/api");
    private final SimpleUrlAuthenticationFailureHandler failure =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

    public void onSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (wantsJson(request)) {
            var session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            }
            writeJson(response, HttpServletResponse.SC_OK, loginStatus.status());
        } else {
            success.onAuthenticationSuccess(request, response, authentication);
        }
    }

    public void onFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        if (wantsJson(request)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    Map.of("detail", "Nieprawidłowy e-mail lub hasło"));
        } else {
            failure.onAuthenticationFailure(request, response, exception);
        }
    }

    private boolean wantsJson(HttpServletRequest request) {
        try {
            return MediaType.parseMediaTypes(Collections.list(request.getHeaders(HttpHeaders.ACCEPT))).stream()
                    .anyMatch(type -> type.getQualityValue() > 0 && MediaType.APPLICATION_JSON.includes(type));
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), body);
    }
}
