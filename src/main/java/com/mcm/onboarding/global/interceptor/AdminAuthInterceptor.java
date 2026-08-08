package com.mcm.onboarding.global.interceptor;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    @Value("${admin.api-key}")
    private String adminApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String providedKey = request.getHeader(ADMIN_KEY_HEADER);

        if (providedKey == null || providedKey.isBlank() || !providedKey.equals(adminApiKey)) {
            throw new BusinessException(ErrorCode.ADMIN_KEY_INVALID);
        }

        return true;
    }
}
