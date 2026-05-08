package com.ttng.demo_web_auth.web

import org.springframework.http.ResponseEntity
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/passkeys")
class PasskeyController(
    private val userEntityRepository: PublicKeyCredentialUserEntityRepository,
    private val credentialRepository: UserCredentialRepository,
) {

    @GetMapping
    fun list(principal: Principal): ResponseEntity<Any> {
        val userEntity = userEntityRepository.findByUsername(principal.name)
            ?: return ResponseEntity.ok(emptyList<Any>())
        val credentials = credentialRepository.findByUserId(userEntity.id)
        return ResponseEntity.ok(credentials.map { cred ->
            mapOf(
                "id" to cred.credentialId.toBase64UrlString(),
                "label" to cred.label,
                "created" to cred.created.toString(),
                "lastUsed" to cred.lastUsed.toString(),
                "transports" to cred.transports.map { it.value },
            )
        })
    }

    @DeleteMapping("/{credentialId}")
    fun delete(@PathVariable credentialId: String, principal: Principal): ResponseEntity<Any> {
        val userEntity = userEntityRepository.findByUsername(principal.name)
            ?: return ResponseEntity.notFound().build()

        val credBytes = Bytes(java.util.Base64.getUrlDecoder().decode(credentialId))
        val cred = credentialRepository.findByCredentialId(credBytes)
            ?: return ResponseEntity.notFound().build()

        if (cred.userEntityUserId != userEntity.id) {
            return ResponseEntity.status(403).body(mapOf("error" to "Forbidden"))
        }

        credentialRepository.delete(credBytes)
        return ResponseEntity.noContent().build()
    }
}
