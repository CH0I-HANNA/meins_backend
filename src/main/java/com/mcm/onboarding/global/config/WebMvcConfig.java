package com.mcm.onboarding.global.config;

import com.mcm.onboarding.global.interceptor.AdminAuthInterceptor;
import com.mcm.onboarding.global.interceptor.OwnerAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OwnerAuthInterceptor ownerAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownerAuthInterceptor)
            // 오너 인증이 필요한 엔드포인트만 적용
            .addPathPatterns(
                "/api/tags/*/home",
                "/api/tags/*/chat",
                "/api/tags/*/chat/**"
            )
            // 소유권 등록은 인증 전 단계이므로 제외
            .excludePathPatterns(
                "/api/tags/*/ownership"
            );

        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/admin/**");
    }
}
