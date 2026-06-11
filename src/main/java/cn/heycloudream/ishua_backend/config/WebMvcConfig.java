package cn.heycloudream.ishua_backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：注册 JWT 鉴权拦截器及其白名单路径。
 *
 * @author C1ouD
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final RoleAuthInterceptor roleAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 用户注册与登录
                        "/api/v1/users/register/email-code",
                        "/api/v1/users/register",
                        "/api/v1/users/login",
                        // 题库大厅（公开题库分页列表，无需登录）
                        "/api/v1/question-banks/public",
                        "/api/v1/bank-nodes/public/roots",
                        "/api/v1/bank-nodes/public/tree",
                        // 公开热点题库详情（无需登录）
                        "/api/v1/question-banks/*/hot-practice-detail",
                        "/api/v1/bank-nodes/*/hot-practice-detail",
                        // Swagger / OpenAPI 文档
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                );

        registry.addInterceptor(roleAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        // 用户注册与登录
                        "/api/v1/users/register/email-code",
                        "/api/v1/users/register",
                        "/api/v1/users/login",
                        // 题库大厅（公开题库分页列表，无需登录）
                        "/api/v1/question-banks/public",
                        "/api/v1/bank-nodes/public/roots",
                        "/api/v1/bank-nodes/public/tree",
                        // 公开热点题库详情（无需登录）
                        "/api/v1/question-banks/*/hot-practice-detail",
                        "/api/v1/bank-nodes/*/hot-practice-detail",
                        // Swagger / OpenAPI 文档
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**"
                );
    }
}
