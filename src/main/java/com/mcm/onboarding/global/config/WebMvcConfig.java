package com.mcm.onboarding.global.config;

import com.mcm.onboarding.global.interceptor.AdminAuthInterceptor;
import com.mcm.onboarding.global.interceptor.OwnerAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OwnerAuthInterceptor ownerAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    // 프론트 배포 도메인. 여러 개면 쉼표로 구분해 CORS_ALLOWED_ORIGINS에 주입.
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownerAuthInterceptor)
            // 오너 인증이 필요한 엔드포인트만 적용.
            // 소유권 등록(POST /api/tags/*/ownership)은 인증 전 단계라 여기 포함되지 않는다.
            .addPathPatterns(
                "/api/tags/*/ownership/me",
                "/api/tags/*/ownership/transfer-code",
                "/api/tags/*/chat",
                "/api/tags/*/chat/**"
            );

        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Authorization");
    }
}
