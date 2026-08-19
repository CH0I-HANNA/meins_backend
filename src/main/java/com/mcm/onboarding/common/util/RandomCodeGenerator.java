package com.mcm.onboarding.common.util;

import java.security.SecureRandom;

// 사람이 직접 입력/타이핑하는 코드(인증코드·양도코드·ownerSecret)가 공유하는 랜덤 코드 생성기.
public final class RandomCodeGenerator {

    // 혼동되는 0/O/1/I 제외.
    public static final String ALPHANUMERIC_NO_AMBIGUOUS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomCodeGenerator() {}

    public static String randomCode(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
