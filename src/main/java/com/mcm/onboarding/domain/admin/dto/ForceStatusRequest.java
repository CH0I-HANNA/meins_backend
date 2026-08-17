package com.mcm.onboarding.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "태그 상태 강제 변경 요청")
public record ForceStatusRequest(
    @NotNull
    @Schema(description = """
        UNLOCK: 잠금/실패횟수만 초기화 (등록 상태 유지) — 운영 중 잠금 문의 대응
        UNLOCK_RECOVERY: 잠금 해제 + 등록 이력 초기화 (재등록 가능 상태로) — 토큰 분실 등 완전 복구
        REGISTERED: 데모용 — 실제 등록 절차 없이 등록 상태로 강제 세팅
        UNREGISTERED: 데모용 — 미등록 상태로 강제 리셋 (크레딧/대화이력도 초기화)
        RESET_CREDIT: 챗 이력·소유 정보는 그대로 두고 남은 크레딧만 현재 한도까지 재충전 — CS 문의 대응
        """, example = "UNLOCK")
    ForceStatusAction action
) {
    public enum ForceStatusAction {
        UNLOCK,
        UNLOCK_RECOVERY,
        REGISTERED,
        UNREGISTERED,
        RESET_CREDIT
    }
}
