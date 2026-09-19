package com.ichsprechedeutsch.common.security;

import com.ichsprechedeutsch.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Guvenlik zinciri istegi denetleyiciye ulasmadan reddettiginde devreye girer.
 *
 * Spring Security varsayilani bos govdeli 401 doner; o zaman istemci neyin
 * yanlis oldugunu bilemez ve genel bir mesaj gostermek zorunda kalir.
 * Burada ayni ApiError bicimini uretiriz, boylece tum hatalar tek bicimde olur.
 *
 * Reddin GERCEK sebebi sadece sunucu log'una yazilir: istemciye "token
 * gecersiz" demek yeterli, imza mi suresi mi sorunlu oldugunu soylemek
 * saldirgana bilgi vermekten baska ise yaramaz.
 */
@Component
public class JsonAuthErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonAuthErrorHandlers.class);

    private final ObjectMapper objectMapper;

    public JsonAuthErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        log.warn("Kimlik dogrulama reddedildi: {} {} -> {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());

        write(response, ApiError.of(401, "UNAUTHORIZED",
                "Oturumun gecersiz veya suresi dolmus. Tekrar giris yap."));
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Erisim reddedildi: {} {} -> {}",
                request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage());

        write(response, ApiError.of(403, "FORBIDDEN", "Bu islem icin yetkin yok"));
    }

    private void write(HttpServletResponse response, ApiError body) throws IOException {
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
