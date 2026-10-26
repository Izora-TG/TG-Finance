package com.tgfinance.server

/** All configuration comes from environment variables (12-factor style). */
data class ServerConfig(
    val port: Int = 8080,
    /** JDBC URL, e.g. jdbc:postgresql://db:5432/tgfinance or jdbc:sqlite:/data/tgfinance.db */
    val databaseUrl: String = "jdbc:sqlite:./data/tgfinance.db",
    val databaseUser: String? = null,
    val databasePassword: String? = null,
    val jwtSecret: String,
    val jwtIssuer: String = "tg-finance",
    val tokenTtlHours: Long = 24L * 30,
    val allowRegistration: Boolean = true,
    /** Honour X-Forwarded-For from the reverse proxy (only enable behind a proxy!). */
    val trustProxy: Boolean = false,
    val maxLoginAttempts: Int = 5,
    val loginLockMinutes: Long = 15,
    val maxPushRecords: Int = 1000,
    val maxPayloadBytes: Int = 64 * 1024,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            val secret = env["JWT_SECRET"]
            require(!secret.isNullOrBlank() && secret.length >= 32) {
                "JWT_SECRET must be set to a random value of at least 32 characters"
            }
            return ServerConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:sqlite:./data/tgfinance.db",
                databaseUser = env["DATABASE_USER"],
                databasePassword = env["DATABASE_PASSWORD"],
                jwtSecret = secret,
                jwtIssuer = env["JWT_ISSUER"] ?: "tg-finance",
                tokenTtlHours = env["TOKEN_TTL_HOURS"]?.toLongOrNull() ?: (24L * 30),
                allowRegistration = env["ALLOW_REGISTRATION"]?.toBooleanStrictOrNull() ?: true,
                trustProxy = env["TRUST_PROXY"]?.toBooleanStrictOrNull() ?: false,
                maxLoginAttempts = env["MAX_LOGIN_ATTEMPTS"]?.toIntOrNull() ?: 5,
                loginLockMinutes = env["LOGIN_LOCK_MINUTES"]?.toLongOrNull() ?: 15,
            )
        }
    }
}
