package com.mcm.onboarding.common.util;

// 태그 코드(XXXX-XXXX), 인증 코드(XXXX-XXXX-XXXX)는 대소문자를 구분하지 않는다.
// 저장/생성 시 항상 대문자이므로, 사용자 입력은 여기서 대문자로 정규화한 뒤 비교/조회한다.
public final class CodeNormalizer {

    private CodeNormalizer() {}

    public static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
