package com.mcm.onboarding.global.interceptor;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class OwnerAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer mcm:own:";

    private final TagRepository tagRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 토큰 형식: mcm:own:{tagCode}:{authCode}
        String[] parts = authorization.substring(BEARER_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        String tokenTagCode = CodeNormalizer.normalize(parts[0]);
        String tokenAuthCode = CodeNormalizer.normalizeAuthCode(parts[1]);

        // 토큰은 태그 1개에만 묶인다 — A태그 토큰으로 B태그 오너 리소스에 접근하면 거부.
        // 실패 원인(형식/불일치/미등록)을 구분해 노출하지 않고 전부 TOKEN_INVALID로 통일한다.
        String pathTagCode = CodeNormalizer.normalize(extractTagCodeFromPath(request.getRequestURI()));
        if (pathTagCode == null || !tokenTagCode.equals(pathTagCode)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // tagCode는 QR로 누구나 알 수 있는 공개값이므로, authCode+등록상태까지 DB에서 실제로 검증한다.
        Tag tag = tagRepository.findByTagCode(tokenTagCode).orElse(null);
        if (tag == null || !tag.isRegistered() || !tag.getAuthCode().equals(tokenAuthCode)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        return true;
    }

    // URI: /api/tags/{tagCode}/ownership/me or /api/tags/{tagCode}/chat/...
    private String extractTagCodeFromPath(String uri) {
        String[] parts = uri.split("/");
        // parts: ["", "api", "tags", "{tagCode}", ...]
        if (parts.length >= 4 && "tags".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
