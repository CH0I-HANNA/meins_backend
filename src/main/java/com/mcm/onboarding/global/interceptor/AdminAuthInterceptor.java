package com.mcm.onboarding.global.interceptor;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    @Value("${admin.api-key}")
    private String adminApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String providedKey = request.getHeader(ADMIN_KEY_HEADER);

        if (providedKey == null || providedKey.isBlank() || !constantTimeEquals(providedKey, adminApiKey)) {
            throw new BusinessException(ErrorCode.ADMIN_KEY_INVALID);
        }

        return true;
    }

    // 모든 /admin/** 요청이 공유하는 단일 고권한 비밀키라, String.equals()의 첫 불일치
    // 문자에서 바로 끝나는 short-circuit 특성이 타이밍 사이드채널이 된다. 상수시간 비교로 방지.
    private boolean constantTimeEquals(String provided, String actual) {
        return MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
