package com.ttng.demo_web_auth.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val username: String,

    @Column(nullable = false)
    val password: String,

    @Column(nullable = false)
    val firstName: String,

    @Column(nullable = false)
    val lastName: String,

    @Column(nullable = false)
    val enabled: Boolean = true,
)
