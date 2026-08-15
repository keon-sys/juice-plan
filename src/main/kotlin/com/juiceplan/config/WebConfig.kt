package com.juiceplan.config

import com.juiceplan.auth.AuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(private val authInterceptor: AuthInterceptor) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/sources", "/sources/**",
                "/plan", "/plan/**",
                "/day", "/day/**",
                "/trip/**",
                "/api/**"
            )
    }
}
