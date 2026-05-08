package com.ttng.demo_web_auth.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api")
class AuthController(private val authenticationManager: AuthenticationManager) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, httpRequest: HttpServletRequest): ResponseEntity<Any> {
        return try {
            val auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password)
            )
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = auth
            SecurityContextHolder.setContext(context)
            httpRequest.getSession(true)
                .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
            ResponseEntity.ok(mapOf("username" to auth.name))
        } catch (ex: BadCredentialsException) {
            ResponseEntity.status(401).body(mapOf("error" to "Invalid credentials"))
        }
    }

    @GetMapping("/me")
    fun me(principal: Principal?): ResponseEntity<Any> =
        if (principal != null) ResponseEntity.ok(mapOf("username" to principal.name))
        else ResponseEntity.status(401).body(mapOf("error" to "Unauthorized"))

    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.ok(mapOf("message" to "Logged out"))
    }

    data class LoginRequest(val username: String, val password: String)
}
