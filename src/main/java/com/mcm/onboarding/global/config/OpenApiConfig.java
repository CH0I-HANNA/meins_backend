package com.mcm.onboarding.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "OwnerToken";
    private static final String ADMIN_KEY_SCHEME = "AdminKey";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("MCM Onboarding API")
                .description("""
                    MCM 제품 QR 코드 기반 온보딩 서비스 API

                    **인증 방식 1**: 소유권 등록/이전 후 발급된 Bearer 토큰 사용
                    - 토큰 형식: `mcm:own:{tagCode}:{ownerSecret}`
                    - Authorization 헤더: `Bearer mcm:own:{tagCode}:{ownerSecret}`
                    - tagCode는 QR로 공개되지만 ownerSecret은 등록/이전 시에만 응답으로 내려가므로, 그 절차 없이는 토큰을 계산할 수 없음
                    - `ownerSecret`은 실물에 인쇄된 `authCode`와는 별개의 랜덤 값이며, 등록 시 최초 발급되고 소유권 이전 시마다 재발급(회전)됨.
                      `authCode`는 절대 바뀌지 않는 값이라 토큰에 그대로 쓰면 "이전 소유자 토큰 무효화"를 구현할 수 없었기 때문에,
                      회전 가능한 별도의 secret으로 분리함 — 이전이 성공하면 새 `ownerSecret`이 발급되고, 이전 소유자가 갖고 있던 옛 토큰은
                      더 이상 DB 값과 일치하지 않아 자동으로 `401 TOKEN_INVALID` 처리됨

                    **인증 방식 2**: 관리자(`/admin/**`) API는 고정 키 사용
                    - 헤더: `X-Admin-Key: {ADMIN_KEY}`

                    **에러 응답 공통 포맷**
                    ```json
                    { "code": "ERR_001", "message": "에러 메시지", "traceId": "uuid" }
                    ```
                    """)
                .version("v1.0"))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("mcm:own:{tagCode}:{ownerSecret}")
                    .description("""
                        소유권 등록(POST /ownership) 또는 이전(POST /ownership/transfer) 후 발급된 토큰. \
                        형식: `mcm:own:{tagCode}:{ownerSecret}`.

                        ownerSecret은 실물 인증코드(authCode)와 분리된 회전 가능한 값으로, 이전이 일어날 때마다 \
                        재발급된다 — authCode는 절대 바뀌지 않아 토큰에 그대로 쓰면 이전 소유자 토큰을 무효화할 \
                        방법이 없기 때문. 이전 성공 시 이전 소유자가 들고 있던 토큰은 재발급된 ownerSecret과 더 \
                        이상 일치하지 않아 자동으로 TOKEN_INVALID 처리된다.\
                        """))
                .addSecuritySchemes(ADMIN_KEY_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-Admin-Key")
                    .description("관리자 API 고정 키 (환경변수 ADMIN_KEY와 동일)")));
    }
}
