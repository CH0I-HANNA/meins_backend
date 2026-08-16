package com.mcm.onboarding.domain.admin.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.common.util.KstTime;
import com.mcm.onboarding.common.util.RandomCodeGenerator;
import com.mcm.onboarding.domain.admin.dto.AdminTagResponse;
import com.mcm.onboarding.domain.admin.dto.BulkCreateRequest;
import com.mcm.onboarding.domain.admin.dto.BulkCreateResponse;
import com.mcm.onboarding.domain.admin.dto.ForceStatusRequest.ForceStatusAction;
import com.mcm.onboarding.domain.chat.entity.ChatCredit;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import com.mcm.onboarding.domain.chat.repository.ChatMessageRepository;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
import com.mcm.onboarding.domain.ownership.repository.OwnershipAttemptRepository;
import com.mcm.onboarding.domain.ownership.repository.OwnershipRepository;
import com.mcm.onboarding.domain.product.entity.Product;
import com.mcm.onboarding.domain.product.repository.ProductRepository;
import com.mcm.onboarding.domain.tag.entity.Tag;
import com.mcm.onboarding.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AdminTagService {

    // 태그 코드: 영문+숫자 전부 허용 (QR로 스캔되며 사람이 직접 타이핑하지 않음)
    private static final String TAG_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TAG_CODE_LENGTH = 8;   // XXXX-XXXX
    private static final int AUTH_CODE_LENGTH = 12; // XXXXXXXXXXXX (하이픈 없음)
    // 관리자가 REGISTERED로 강제 세팅할 때도 오너 토큰이 필요하므로 등록 플로우와 동일한 길이로 발급
    private static final int OWNER_SECRET_LENGTH = 12;
    private static final int GROUP_SIZE = 4;

    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;
    private final OwnershipAttemptRepository ownershipAttemptRepository;
    private final ChatCreditRepository chatCreditRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QrCodeService qrCodeService;

    @Transactional
    public BulkCreateResponse bulkCreate(BulkCreateRequest request) {
        Product product = Product.create(
            request.productName(), request.modelCode(), request.manufacturedYm(), request.material(), request.color(),
            request.saleRegisteredYm(), request.widthCm(), request.depthCm(), request.heightCm(),
            request.imageUrl(), request.thumbnailImageUrl1(), request.thumbnailImageUrl2(),
            request.thumbnailImageUrl3(), request.productPageUrl()
        );
        productRepository.save(product);

        List<BulkCreateResponse.CreatedTag> created = new ArrayList<>(request.quantity());
        for (int i = 0; i < request.quantity(); i++) {
            String tagCode = generateUniqueTagCode();
            String authCode = generateUniqueAuthCode();

            Tag tag = Tag.create(product, tagCode, authCode);
            tagRepository.save(tag);
            ownershipRepository.save(OwnershipRecord.createFor(tag));

            created.add(new BulkCreateResponse.CreatedTag(tagCode, authCode));
        }

        return new BulkCreateResponse(product.getId(), created);
    }

    public List<AdminTagResponse> listTags() {
        LocalDateTime now = KstTime.now();
        return tagRepository.findAll().stream()
            // 잠금은 (태그, IP)별이므로 "현재 잠긴 시도 이력이 하나라도 있으면" 잠김으로 표시한다.
            .map(tag -> AdminTagResponse.of(
                tag,
                ownershipAttemptRepository.existsByTagCodeAndLockedUntilAfter(tag.getTagCode(), now),
                ownershipRepository.findByTag_TagCode(tag.getTagCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR)),
                now
            ))
            .toList();
    }

    public byte[] generateQrPng(String tagCode) {
        Tag tag = findTagOrThrow(tagCode);
        return qrCodeService.generatePng(tag.getTagCode());
    }

    public byte[] generateQrZip() {
        List<Tag> tags = tagRepository.findAll();
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Tag tag : tags) {
                zip.putNextEntry(new ZipEntry(tag.getTagCode() + ".png"));
                zip.write(qrCodeService.generatePng(tag.getTagCode()));
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Transactional
    public void forceStatus(String tagCode, ForceStatusAction action) {
        Tag tag = findTagOrThrow(tagCode);
        String canonicalTagCode = tag.getTagCode();
        OwnershipRecord record = ownershipRepository.findByTag_TagCode(canonicalTagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));

        LocalDateTime now = KstTime.now();

        switch (action) {
            // 잠금 해제 = 해당 태그에 쌓인 (태그, IP) 시도 이력을 모두 제거
            case UNLOCK -> ownershipAttemptRepository.deleteByTagCode(canonicalTagCode);
            case UNLOCK_RECOVERY -> {
                ownershipAttemptRepository.deleteByTagCode(canonicalTagCode);
                record.resetForRecovery();
                tag.markUnregistered();
            }
            case REGISTERED -> {
                tag.markRegistered();
                String ownerSecret = RandomCodeGenerator.randomCode(RandomCodeGenerator.ALPHANUMERIC_NO_AMBIGUOUS, OWNER_SECRET_LENGTH);
                record.markRegistered("ADMIN_FORCED", now, ownerSecret);
                chatCreditRepository.findByTagCode(canonicalTagCode)
                    .orElseGet(() -> chatCreditRepository.save(ChatCredit.init(canonicalTagCode, now)));
            }
            case UNREGISTERED -> {
                tag.markUnregistered();
                record.resetForRecovery();
                ownershipAttemptRepository.deleteByTagCode(canonicalTagCode);
                chatCreditRepository.deleteByTagCode(canonicalTagCode);
                chatMessageRepository.deleteByTagCode(canonicalTagCode);
            }
            default -> throw new BusinessException(ErrorCode.ADMIN_INVALID_ACTION);
        }
    }

    // ownership_records.tag_id가 tags에 대한 FK라 태그보다 먼저 지워야 한다.
    // ownership_attempts/chat_credits/chat_messages는 tag_code 문자열 컬럼(FK 아님)이라 순서 무관하지만 함께 정리한다.
    @Transactional
    public void deleteTag(String tagCode) {
        Tag tag = findTagOrThrow(tagCode);
        String canonicalTagCode = tag.getTagCode();

        ownershipAttemptRepository.deleteByTagCode(canonicalTagCode);
        ownershipRepository.deleteByTag_TagCode(canonicalTagCode);
        chatCreditRepository.deleteByTagCode(canonicalTagCode);
        chatMessageRepository.deleteByTagCode(canonicalTagCode);
        tagRepository.delete(tag);
    }

    private Tag findTagOrThrow(String tagCode) {
        return tagRepository.findByTagCode(CodeNormalizer.normalize(tagCode))
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
    }

    private String generateUniqueTagCode() {
        String tagCode;
        do {
            tagCode = groupWithHyphens(RandomCodeGenerator.randomCode(TAG_CODE_ALPHABET, TAG_CODE_LENGTH));
        } while (tagRepository.existsByTagCode(tagCode));
        return tagCode;
    }

    private String generateUniqueAuthCode() {
        String authCode;
        do {
            authCode = RandomCodeGenerator.randomCode(RandomCodeGenerator.ALPHANUMERIC_NO_AMBIGUOUS, AUTH_CODE_LENGTH);
        } while (tagRepository.existsByAuthCode(authCode));
        return authCode;
    }

    // "ABCDEFGH" -> "ABCD-EFGH" (GROUP_SIZE 단위로 하이픈 삽입)
    private String groupWithHyphens(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && i % GROUP_SIZE == 0) {
                sb.append('-');
            }
            sb.append(raw.charAt(i));
        }
        return sb.toString();
    }
}
