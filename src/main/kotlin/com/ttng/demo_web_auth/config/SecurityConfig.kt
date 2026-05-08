package com.ttng.demo_web_auth.config

import com.ttng.demo_web_auth.domain.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
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
            .csrf { it.disable() }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = "application/json"
                    response.writer.write("""{"error":"Unauthorized"}""")
                }
                ex.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = "application/json"
                    response.writer.write("""{"error":"Forbidden"}""")
                }
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/api/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/logout").permitAll()
                    .requestMatchers("/webauthn/authenticate/options", "/login/webauthn").permitAll()
                    .requestMatchers("/", "/index.html", "/*.js", "/*.css").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun publicKeyCredentialUserEntityRepository(jdbcOperations: JdbcOperations): PublicKeyCredentialUserEntityRepository =
        JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations)

    @Bean
    fun userCredentialRepository(jdbcOperations: JdbcOperations): UserCredentialRepository =
        JdbcUserCredentialRepository(jdbcOperations)

    @Bean
    fun userDetailsService(userRepository: UserRepository): UserDetailsService =
        UserDetailsService { username ->
            userRepository.findByUsername(username)
                ?: throw UsernameNotFoundException("User not found: $username")
        }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager =
        authConfig.authenticationManager
}
