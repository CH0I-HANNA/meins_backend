package com.mcm.onboarding.common.util;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;

import java.util.regex.Pattern;

// 기획 명세 1-4(데이터 형식):
// - tagCode: XXXX-XXXX (영문 대문자 + 숫자)
// - 인증 코드: 12자, 0/O/1/I 제외. 프론트가 하이픈 제거 후 보내지만 서버도 방어적으로 제거한다.
// 저장/생성 시 항상 대문자이므로, 사용자 입력은 여기서 대문자로 정규화한 뒤 비교/조회한다.
public final class CodeNormalizer {

    private static final Pattern TAG_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4}-[A-Z0-9]{4}$");

    private CodeNormalizer() {}

    public static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    // 인증 코드는 하이픈 유무와 무관하게 동일한 값으로 취급한다 (XXXX-XXXX-XXXX / XXXXXXXXXXXX 둘 다 허용).
    public static String normalizeAuthCode(String code) {
        String normalized = normalize(code);
        return normalized == null ? null : normalized.replace("-", "");
    }

    // 형식이 어긋나면 DB를 조회하기 전에 TAG_INVALID_FORMAT(400)으로 끊는다.
    // 프론트는 TAG_NOT_FOUND와 동일하게 "태그 확인 불가" 화면으로 처리한다.
    public static String normalizeAndValidateTagCode(String rawTagCode) {
        String tagCode = normalize(rawTagCode);
        if (tagCode == null || !TAG_CODE_PATTERN.matcher(tagCode).matches()) {
            throw new BusinessException(ErrorCode.TAG_INVALID_FORMAT);
        }
        return tagCode;
    }
}
