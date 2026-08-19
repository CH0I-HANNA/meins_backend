package com.mcm.onboarding.domain.ownership.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.common.util.RandomCodeGenerator;
import com.mcm.onboarding.domain.chat.entity.ChatCredit;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import com.mcm.onboarding.domain.chat.repository.ChatMessageRepository;
import com.mcm.onboarding.domain.ownership.dto.OwnershipResponse;
import com.mcm.onboarding.domain.ownership.dto.TransferCodeResponse;
import com.mcm.onboarding.domain.ownership.entity.OwnershipAttempt;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipAttemptRepository;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class OwnershipService {

    // 등록 시 발급되는 ownerSecret과 소유권 이전 코드는 인증코드와 동일한 문자집합·자릿수를 쓴다
    // (RandomCodeGenerator.ALPHANUMERIC_NO_AMBIGUOUS — 사람이 직접 입력/보관하는 코드 공통 규격).
    private static final int OWNER_SECRET_LENGTH = 12;
    private static final int TRANSFER_CODE_LENGTH = 12;

    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;
    private final OwnershipAttemptRepository ownershipAttemptRepository;
    private final ChatCreditRepository chatCreditRepository;
    private final ChatMessageRepository chatMessageRepository;

    // tags/ownership_records는 관리자 bulk-create 시 1:1로 항상 함께 생성되므로
    // 여기서는 OwnershipRecord가 없는 상태(null)를 다루지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public OwnershipResponse register(String rawTagCode, String rawCode, String clientIp) {
        String tagCode = CodeNormalizer.normalizeAndValidateTagCode(rawTagCode);
        String authCode = CodeNormalizer.normalizeAuthCode(rawCode);
        LocalDateTime now = KstTime.now();
        String ipHash = hashIp(clientIp);

        Tag tag = tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        // 실패 누적과 잠금 판정은 (태그, IP) 단위로 서버가 관리한다.
        // 프론트 세션 기준이면 브라우저를 닫거나 시크릿 창을 열어 우회할 수 있고,
        // tagCode 단독이면 제3자가 남의 태그를 골라 잠글 수 있다.
        OwnershipAttempt attempt = ownershipAttemptRepository.findByTagCodeAndIpHash(tagCode, ipHash)
            .orElseGet(() -> OwnershipAttempt.of(tagCode, ipHash));

        attempt.clearExpiredLock(now); // 24시간 경과분은 자동 해제
        if (attempt.isLockedAt(now)) {
            ownershipAttemptRepository.save(attempt);
            throw BusinessException.codeLocked(attempt.getLockedUntil());
        }

        // 이미 소유권 등록된 태그
        if (tag.isRegistered()) {
            failAndMaybeLock(attempt, now, ErrorCode.ALREADY_REGISTERED);
        }

        // 인증 코드 불일치 — 실물을 실제로 가진 사람만 등록 가능하도록 검증.
        // 이미 사용된 코드도 여기서 동일하게 걸리므로 "사용 여부"가 노출되지 않는다(코드 열거 방지).
        // 비교도 상수시간으로 해야 타이밍 차이로 코드가 한 글자씩 새는 걸 막는다(5회 잠금이 있어도
        // 잠금 전 몇 번의 요청만으로 타이밍 샘플을 모을 수 있으므로 방어를 겹쳐둔다).
        if (!constantTimeEquals(tag.getAuthCode(), authCode)) {
            failAndMaybeLock(attempt, now, ErrorCode.CODE_MISMATCH);
        }

        // 소유권 등록 처리
        String ownerSecret = RandomCodeGenerator.randomCode(RandomCodeGenerator.ALPHANUMERIC_NO_AMBIGUOUS, OWNER_SECRET_LENGTH);
        record.markRegistered(ipHash, now, ownerSecret);
        ownershipRepository.save(record);

        attempt.reset(now);
        ownershipAttemptRepository.save(attempt);

        tag.markRegistered();
        tagRepository.save(tag);

        // 크레딧 초기화 (중복 방지)
        ChatCredit credit = chatCreditRepository.findByTagCode(tagCode)
            .orElseGet(() -> ChatCredit.init(tagCode, now));
        chatCreditRepository.save(credit);

        return OwnershipResponse.of(tagCode, ownerSecret, record.getRegisteredAt());
    }

    // 오너 인증 필요(컨트롤러가 인터셉터로 검증) — 활성 코드가 있으면 재발급하지 않고 그대로 반환한다.
    @Transactional
    public TransferCodeResponse issueOrFetchTransferCode(String rawTagCode) {
        String tagCode = CodeNormalizer.normalizeAndValidateTagCode(rawTagCode);
        LocalDateTime now = KstTime.now();

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        if (!record.hasActiveTransferCode(now)) {
            String code = RandomCodeGenerator.randomCode(RandomCodeGenerator.ALPHANUMERIC_NO_AMBIGUOUS, TRANSFER_CODE_LENGTH);
            record.issueTransferCode(code, now);
            ownershipRepository.save(record);
        }

        return TransferCodeResponse.of(record.getTransferCode(), record.getTransferCodeIssuedAt(), record.getTransferCodeExpiresAt());
    }

    // 활성 코드가 없어도 그냥 성공(no-op) — 재발급을 다시 가능하게 만든다.
    @Transactional
    public void cancelTransferCode(String rawTagCode) {
        String tagCode = CodeNormalizer.normalizeAndValidateTagCode(rawTagCode);

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        record.cancelTransferCode();
        ownershipRepository.save(record);
    }

    // 새 소유자는 아직 토큰이 없으므로 인증 불필요 — register()와 동일한 뼈대(잠금 판정 → 코드 검증 → 반영).
    @Transactional(noRollbackFor = BusinessException.class)
    public OwnershipResponse transfer(String rawTagCode, String rawCode, String clientIp) {
        String tagCode = CodeNormalizer.normalizeAndValidateTagCode(rawTagCode);
        String code = CodeNormalizer.normalizeAuthCode(rawCode);
        LocalDateTime now = KstTime.now();
        String ipHash = hashIp(clientIp);

        tagRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        OwnershipRecord record = ownershipRepository.findByTag_TagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        // 양도코드 잠금도 구매인증 잠금과 동일 (tagCode, ipHash) 테이블을 공유한다 — 태그 상태상
        // 두 잠금이 동시에 겹칠 일이 없어 안전하다.
        OwnershipAttempt attempt = ownershipAttemptRepository.findByTagCodeAndIpHash(tagCode, ipHash)
            .orElseGet(() -> OwnershipAttempt.of(tagCode, ipHash));

        attempt.clearExpiredLock(now);
        if (attempt.isLockedAt(now)) {
            ownershipAttemptRepository.save(attempt);
            throw BusinessException.codeLocked(attempt.getLockedUntil());
        }

        // 만료/취소/불일치를 모두 CODE_MISMATCH로 동일하게 처리한다 (활성 코드 여부가 노출되지 않도록).
        if (!record.hasActiveTransferCode(now) || !constantTimeEquals(record.getTransferCode(), code)) {
            failAndMaybeLock(attempt, now, ErrorCode.CODE_MISMATCH);
        }

        String newOwnerSecret = RandomCodeGenerator.randomCode(RandomCodeGenerator.ALPHANUMERIC_NO_AMBIGUOUS, OWNER_SECRET_LENGTH);
        record.applyTransfer(ipHash, now, newOwnerSecret);
        ownershipRepository.save(record);

        attempt.reset(now);
        ownershipAttemptRepository.save(attempt);

        // 신규 소유자는 챗 이력을 승계하지 않고, 크레딧도 축소된 한도로 재발급된다.
        ChatCredit credit = chatCreditRepository.findByTagCode(tagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        credit.resetForTransfer(now);
        chatCreditRepository.save(credit);
        chatMessageRepository.deleteByTagCode(tagCode);

        return OwnershipResponse.of(tagCode, newOwnerSecret, record.getRegisteredAt());
    }

    // 실패 카운트 증가 후, 5회 도달 시 CODE_LOCKED로 승격하고 그렇지 않으면 원래 에러를 던진다.
    private void failAndMaybeLock(OwnershipAttempt attempt, LocalDateTime now, ErrorCode originalError) {
        attempt.incrementFailure(now);
        ownershipAttemptRepository.save(attempt);

        if (attempt.isLockedAt(now)) {
            throw BusinessException.codeLocked(attempt.getLockedUntil());
        }
        if (originalError == ErrorCode.CODE_MISMATCH) {
            throw BusinessException.codeMismatch(attempt.remainingAttempts());
        }
        throw new BusinessException(originalError);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hashIp(String rawIp) {
        // X-Forwarded-For는 "client, proxy1, proxy2" 형태로 올 수 있다. Railway는 신뢰 가능한
        // 단일 홉으로, 클라이언트가 무엇을 보내든 실제 관측 IP를 맨 뒤에 append한다. 맨 앞(leftmost)
        // 값은 클라이언트가 자유롭게 조작 가능해 그걸 쓰면 잠금 키(ip_hash)를 마음대로 바꿔가며
        // 5회 실패 잠금을 무한히 우회할 수 있으므로, 신뢰 가능한 맨 뒤(rightmost) 값을 쓴다.
        String[] parts = rawIp.split(",");
        String ip = parts[parts.length - 1].trim();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ip.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return ip;
        }
    }
}
