package com.library.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authentication;

    public WebMvcConfig(AuthenticationInterceptor authentication) {
        this.authentication = authentication;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authentication)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/actuator/**");
    }
}
