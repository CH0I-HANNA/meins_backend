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

                    **인증 방식 1**: 소유권 등록 후 발급된 Bearer 토큰 사용
                    - 토큰 형식: `mcm:own:{tagCode}:{authCode}`
                    - Authorization 헤더: `Bearer mcm:own:{tagCode}:{authCode}`
                    - tagCode는 QR로 공개되지만 authCode는 실물에만 인쇄되어 있어, 등록 절차 없이는 토큰을 계산할 수 없음

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
                    .bearerFormat("mcm:own:{tagCode}:{authCode}")
                    .description("소유권 등록(POST /ownership) 후 발급된 토큰. 형식: `mcm:own:{tagCode}:{authCode}`"))
                .addSecuritySchemes(ADMIN_KEY_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-Admin-Key")
                    .description("관리자 API 고정 키 (환경변수 ADMIN_KEY와 동일)")));
    }
}
