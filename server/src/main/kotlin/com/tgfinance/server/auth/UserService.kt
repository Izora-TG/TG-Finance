package com.tgfinance.server.auth

import com.tgfinance.server.db.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.Base64

data class User(val id: Long, val username: String, val passwordHash: String, val e2eSalt: String)

class UserService(
    private val db: Database,
    private val hasher: PasswordHasher,
    private val random: SecureRandom = SecureRandom(),
) {
    sealed interface RegisterResult {
        data class Created(val user: User) : RegisterResult
        data object UsernameTaken : RegisterResult
        data class Invalid(val reason: String) : RegisterResult
    }

    fun register(username: String, password: String): RegisterResult {
        val name = username.trim()
        validate(name, password)?.let { return RegisterResult.Invalid(it) }
        val hash = hasher.hash(password)
        val salt = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
        return transaction(db) {
            if (findByName(name) != null) return@transaction RegisterResult.UsernameTaken
            val id = Users.insert {
                it[Users.username] = name
                it[passwordHash] = hash
                it[e2eSalt] = salt
                it[createdAt] = System.currentTimeMillis()
            }[Users.id]
            RegisterResult.Created(User(id, name, hash, salt))
        }
    }

    /** Returns the user if the credentials are valid. Always hashes to keep timing uniform. */
    fun authenticate(username: String, password: String): User? {
        val user = transaction(db) { findByName(username.trim()) }
        val ok = hasher.verify(password, user?.passwordHash ?: DUMMY_HASH)
        return user?.takeIf { ok }
    }

    fun get(id: Long): User? = transaction(db) {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }

    private fun findByName(name: String): User? =
        Users.selectAll().where { Users.username eq name }.singleOrNull()?.toUser()

    private fun org.jetbrains.exposed.sql.ResultRow.toUser() =
        User(this[Users.id], this[Users.username], this[Users.passwordHash], this[Users.e2eSalt])

    companion object {
        private val USERNAME = Regex("^[A-Za-z0-9._-]{3,64}$")
        const val MIN_PASSWORD = 10

        fun validate(username: String, password: String): String? = when {
            !USERNAME.matches(username) -> "Username must be 3-64 characters (letters, digits, . _ -)"
            password.length < MIN_PASSWORD -> "Password must have at least $MIN_PASSWORD characters"
            password.length > 1024 -> "Password too long"
            else -> null
        }

        /** Valid hash of a random password, used when the user does not exist. */
        private val DUMMY_HASH: String by lazy { PasswordHasher().hash("dummy-password-${System.nanoTime()}") }
    }
}
