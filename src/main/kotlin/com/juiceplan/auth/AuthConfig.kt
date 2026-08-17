package com.juiceplan.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuthConfig {

    /** APP_PASSWORD 환경변수. 비어 있으면 인증이 통째로 꺼진다. */
    @Bean
    fun authTokens(@Value("\${app.password:}") password: String): AuthTokens = AuthTokens(password)
}
