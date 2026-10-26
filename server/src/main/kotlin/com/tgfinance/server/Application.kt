package com.tgfinance.server

import com.tgfinance.core.sync.CredentialsRequest
import com.tgfinance.core.sync.ErrorResponse
import com.tgfinance.core.sync.PushRequest
import com.tgfinance.core.sync.SyncPaths
import com.tgfinance.core.sync.TokenResponse
import com.tgfinance.core.sync.UserInfo
import com.tgfinance.server.auth.JwtService
import com.tgfinance.server.auth.LoginRateLimiter
import com.tgfinance.server.auth.PasswordHasher
import com.tgfinance.server.auth.UserService
import com.tgfinance.server.db.DatabaseFactory
import com.tgfinance.server.sync.SyncService
import com.tgfinance.server.sync.ValidationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: ServerConfig) {
    val db = DatabaseFactory.connect(config)
    val jwt = JwtService(config)
    val users = UserService(db, PasswordHasher())
    val sync = SyncService(db, config.maxPushRecords, config.maxPayloadBytes)
    val limiter = LoginRateLimiter(config.maxLoginAttempts, config.loginLockMinutes * 60_000L)

    if (config.trustProxy) install(XForwardedHeaders)
    install(DefaultHeaders) { header("X-Content-Type-Options", "nosniff") }
    install(CallLogging) {
        level = Level.INFO
        // Never log request bodies – they contain financial data.
        format { call -> "${call.request.local.method.value} ${call.request.local.uri} -> ${call.response.status()}" }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(StatusPages) {
        exception<ValidationException> { call, e -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "invalid")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid request")) }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal error"))
        }
    }
    install(Authentication) {
        jwt("auth") {
            realm = "tg-finance"
            verifier(jwt.verifier)
            validate { credential -> credential.payload.subject?.toLongOrNull()?.let { JWTPrincipal(credential.payload) } }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized")) }
        }
    }

    routing {
        get(SyncPaths.HEALTH) { call.respond(mapOf("status" to "ok")) }

        post(SyncPaths.REGISTER) {
            if (!config.allowRegistration) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("registration disabled"))
                return@post
            }
            val ipKey = "ip:${call.request.origin.remoteHost}"
            if (rejectIfLocked(call, limiter, ipKey)) return@post
            val body = call.receive<CredentialsRequest>()
            when (val result = users.register(body.username, body.password)) {
                is UserService.RegisterResult.Created -> {
                    val token = jwt.create(result.user.id, result.user.username)
                    call.respond(HttpStatusCode.Created, TokenResponse(token.value, token.expiresAt, result.user.username))
                }
                UserService.RegisterResult.UsernameTaken -> {
                    // Counts as a failure to make username enumeration expensive.
                    limiter.recordFailure(ipKey)
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("username taken"))
                }
                is UserService.RegisterResult.Invalid -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.reason))
            }
        }

        post(SyncPaths.LOGIN) {
            val body = call.receive<CredentialsRequest>()
            val ipKey = "ip:${call.request.origin.remoteHost}"
            val userKey = "user:${body.username.trim().lowercase()}"
            if (rejectIfLocked(call, limiter, ipKey) || rejectIfLocked(call, limiter, userKey)) return@post
            val user = users.authenticate(body.username, body.password)
            if (user == null) {
                limiter.recordFailure(ipKey)
                limiter.recordFailure(userKey)
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid credentials"))
                return@post
            }
            limiter.recordSuccess(ipKey)
            limiter.recordSuccess(userKey)
            val token = jwt.create(user.id, user.username)
            call.respond(TokenResponse(token.value, token.expiresAt, user.username))
        }

        authenticate("auth") {
            get(SyncPaths.ME) {
                val user = users.get(call.userId()) ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unknown user"))
                call.respond(UserInfo(user.username, user.e2eSalt))
            }
            post(SyncPaths.PUSH) {
                val request = call.receive<PushRequest>()
                call.respond(sync.push(call.userId(), request))
            }
            get(SyncPaths.PULL) {
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 500
                call.respond(sync.pull(call.userId(), since, limit))
            }
        }
    }
}

private fun ApplicationCall.userId(): Long = principal<JWTPrincipal>()!!.payload.subject.toLong()

private suspend fun rejectIfLocked(call: ApplicationCall, limiter: LoginRateLimiter, key: String): Boolean {
    val wait = limiter.retryAfter(key)
    if (wait <= 0) return false
    call.response.header(HttpHeaders.RetryAfter, ((wait + 999) / 1000).toString())
    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("too many attempts"))
    return true
}
