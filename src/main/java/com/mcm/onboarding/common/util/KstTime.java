package com.mcm.onboarding.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 기획 명세 1-3(시간 표기): 기준 시간대는 KST.
// - 게스트에게 나가는 시각은 YYYY-MM으로만 (프론트에서 자르면 네트워크 탭에 원본이 남으므로 서버가 자른다)
// - 오너 응답은 ISO 8601 오프셋 표기(2026-03-14T09:22:00+09:00)로 내리고 표시 포맷은 프론트가 결정
public final class KstTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter GUEST_PRECISION = DateTimeFormatter.ofPattern("yyyy-MM");
    // ISO_OFFSET_DATE_TIME은 초가 0이면 초를 생략하므로, 명세 예시와 자릿수를 맞추기 위해 패턴을 고정한다.
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private KstTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    // 게스트 정밀도: YYYY-MM
    public static String toGuestPrecision(LocalDateTime time) {
        return time == null ? null : time.format(GUEST_PRECISION);
    }

    // 오너 정밀도: ISO 8601 + KST 오프셋
    public static String toIso(LocalDateTime time) {
        return time == null ? null : time.atZone(KST).toOffsetDateTime().format(ISO_OFFSET);
    }
}
