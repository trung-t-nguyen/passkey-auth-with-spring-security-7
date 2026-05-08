package com.ttng.demo_web_auth.config

import com.ttng.demo_web_auth.domain.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .webAuthn { webAuthn ->
                webAuthn
                    .rpId("localhost")
                    .rpName("Demo Web Auth")
                    .allowedOrigins("http://localhost:8080")
            }
            .formLogin { }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/login").permitAll()
                    .anyRequest().authenticated()
            }
        return http.build()
    }

    @Bean
    fun userDetailsService(userRepository: UserRepository): UserDetailsService =
        UserDetailsService { username ->
            val user = userRepository.findByUsername(username)
                ?: throw UsernameNotFoundException("User not found: $username")
            User.withUsername(user.username)
                .password(user.password)
                .disabled(!user.enabled)
                .build()
        }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun publicKeyCredentialUserEntityRepository(jdbcOperations: JdbcOperations): PublicKeyCredentialUserEntityRepository =
        JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations)

    @Bean
    fun userCredentialRepository(jdbcOperations: JdbcOperations): UserCredentialRepository =
        JdbcUserCredentialRepository(jdbcOperations)
}
