package com.mcm.onboarding.global.interceptor;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@RequiredArgsConstructor
public class OwnerAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer mcm:own:";

    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // 토큰 형식: mcm:own:{tagCode}:{ownerSecret}
        String[] parts = authorization.substring(BEARER_PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        String tokenTagCode = CodeNormalizer.normalize(parts[0]);
        String tokenSecret = CodeNormalizer.normalizeAuthCode(parts[1]);

        // 토큰은 태그 1개에만 묶인다 — A태그 토큰으로 B태그 오너 리소스에 접근하면 거부.
        // 실패 원인(형식/불일치/미등록)을 구분해 노출하지 않고 전부 TOKEN_INVALID로 통일한다.
        String pathTagCode = CodeNormalizer.normalize(extractTagCodeFromPath(request.getRequestURI()));
        if (pathTagCode == null || !tokenTagCode.equals(pathTagCode)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // tagCode는 QR로 누구나 알 수 있는 공개값이므로, ownerSecret+등록상태까지 DB에서 실제로 검증한다.
        Tag tag = tagRepository.findByTagCode(tokenTagCode).orElse(null);
        if (tag == null || !tag.isRegistered()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tokenTagCode).orElse(null);
        if (record == null || record.getOwnerSecret() == null || !constantTimeEquals(record.getOwnerSecret(), tokenSecret)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        return true;
    }

    // 소유권 이전으로 ownerSecret이 회전되면 이전 토큰은 이 비교에서 자연히 탈락한다.
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
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
