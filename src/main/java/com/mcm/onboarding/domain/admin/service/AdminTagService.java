package com.mcm.onboarding.domain.admin.service;

import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import com.mcm.onboarding.common.util.CodeNormalizer;
import com.mcm.onboarding.domain.admin.dto.AdminTagResponse;
import com.mcm.onboarding.domain.admin.dto.BulkCreateRequest;
import com.mcm.onboarding.domain.admin.dto.BulkCreateResponse;
import com.mcm.onboarding.domain.admin.dto.ForceStatusRequest.ForceStatusAction;
import com.mcm.onboarding.domain.chat.repository.ChatCreditRepository;
import com.mcm.onboarding.domain.chat.repository.ChatMessageRepository;
import com.mcm.onboarding.domain.ownership.entity.OwnershipRecord;
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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AdminTagService {

    // 태그 코드: 영문+숫자 전부 허용 (QR로 스캔되며 사람이 직접 타이핑하지 않음)
    private static final String TAG_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    // 인증 코드: 사람이 직접 입력하므로 혼동되는 0/O/1/I 제외
    private static final String AUTH_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TAG_CODE_LENGTH = 8;   // XXXX-XXXX
    private static final int AUTH_CODE_LENGTH = 12; // XXXXXXXXXXXX (하이픈 없음)
    private static final int GROUP_SIZE = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductRepository productRepository;
    private final TagRepository tagRepository;
    private final OwnershipRepository ownershipRepository;
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
        return tagRepository.findAll().stream()
            .map(tag -> {
                boolean locked = ownershipRepository.findByTag_TagCode(tag.getTagCode())
                    .map(OwnershipRecord::isLocked)
                    .orElse(false);
                return AdminTagResponse.of(tag, locked);
            })
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
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void forceStatus(String tagCode, ForceStatusAction action) {
        Tag tag = findTagOrThrow(tagCode);
        String canonicalTagCode = tag.getTagCode();
        OwnershipRecord record = ownershipRepository.findByTag_TagCode(canonicalTagCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        switch (action) {
            case UNLOCK -> record.unlock();
            case UNLOCK_RECOVERY -> {
                record.resetForRecovery();
                tag.markUnregistered();
            }
            case REGISTERED -> {
                tag.markRegistered();
                record.markRegistered("ADMIN_FORCED");
                chatCreditRepository.findByTagCode(canonicalTagCode)
                    .orElseGet(() -> chatCreditRepository.save(
                        com.mcm.onboarding.domain.chat.entity.ChatCredit.init(canonicalTagCode)));
            }
            case UNREGISTERED -> {
                tag.markUnregistered();
                record.resetForRecovery();
                chatCreditRepository.deleteByTagCode(canonicalTagCode);
                chatMessageRepository.deleteByTagCode(canonicalTagCode);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_FORCE_STATUS_ACTION);
        }
    }

    private Tag findTagOrThrow(String tagCode) {
        return tagRepository.findByTagCode(CodeNormalizer.normalize(tagCode))
            .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
    }

    private String generateUniqueTagCode() {
        String tagCode;
        do {
            tagCode = groupWithHyphens(randomCode(TAG_CODE_ALPHABET, TAG_CODE_LENGTH));
        } while (tagRepository.existsByTagCode(tagCode));
        return tagCode;
    }

    private String generateUniqueAuthCode() {
        String authCode;
        do {
            authCode = randomCode(AUTH_CODE_ALPHABET, AUTH_CODE_LENGTH);
        } while (tagRepository.existsByAuthCode(authCode));
        return authCode;
    }

    private String randomCode(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
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
