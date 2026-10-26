package com.tgfinance.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.tgfinance.server.ServerConfig
import java.util.Date

class JwtService(private val config: ServerConfig, private val clock: () -> Long = System::currentTimeMillis) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(config.jwtIssuer).withAudience(AUDIENCE).build()

    data class Token(val value: String, val expiresAt: Long)

    fun create(userId: Long, username: String): Token {
        val expiresAt = clock() + config.tokenTtlHours * 3_600_000L
        val token = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withClaim(CLAIM_USERNAME, username)
            .withIssuedAt(Date(clock()))
            .withExpiresAt(Date(expiresAt))
            .sign(algorithm)
        return Token(token, expiresAt)
    }

    companion object {
        const val AUDIENCE = "tg-finance-app"
        const val CLAIM_USERNAME = "username"
    }
}
