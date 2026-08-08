package com.mcm.onboarding.domain.admin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mcm.onboarding.common.exception.BusinessException;
import com.mcm.onboarding.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class QrCodeService {

    private static final int QR_SIZE = 300;

    // QR_URL_TEMPLATE 환경변수로 주입. 로컬은 localhost, 배포 시 실제 프론트 도메인으로 교체.
    // 설정 자체가 없으면 tagCode 원문을 그대로 인코딩.
    @Value("${app.qr.url-template:{tagCode}}")
    private String urlTemplate;

    public byte[] generatePng(String tagCode) {
        String content = urlTemplate.replace("{tagCode}", tagCode);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
