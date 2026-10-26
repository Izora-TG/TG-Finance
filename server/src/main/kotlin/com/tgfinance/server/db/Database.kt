package com.tgfinance.server.db

import com.tgfinance.server.ServerConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.Connection

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val e2eSalt = varchar("e2e_salt", 64)
    /** Per-user change counter; every stored record gets the next value (sync cursor). */
    val seq = long("seq").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Records : Table("records") {
    val userId = long("user_id").references(Users.id)
    val type = varchar("type", 32)
    val recordId = varchar("record_id", 128)
    val updatedAt = long("updated_at")
    val deleted = bool("deleted")
    val deviceId = varchar("device_id", 64)
    val payload = text("payload")
    val seq = long("seq")
    override val primaryKey = PrimaryKey(userId, type, recordId)

    init {
        index("records_user_seq", false, userId, seq)
    }
}

/** Server-side log of rejected (older) writes. */
object Conflicts : Table("conflicts") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val type = varchar("type", 32)
    val recordId = varchar("record_id", 128)
    val incomingUpdatedAt = long("incoming_updated_at")
    val incomingDeviceId = varchar("incoming_device_id", 64)
    val storedUpdatedAt = long("stored_updated_at")
    val storedDeviceId = varchar("stored_device_id", 64)
    val detectedAt = long("detected_at")
    override val primaryKey = PrimaryKey(id)
}

object DatabaseFactory {
    fun connect(config: ServerConfig): Database {
        val isSqlite = config.databaseUrl.startsWith("jdbc:sqlite:")
        if (isSqlite) {
            val path = config.databaseUrl.removePrefix("jdbc:sqlite:")
            if (path != ":memory:" && !path.startsWith("file:")) File(path).absoluteFile.parentFile?.mkdirs()
        }
        val hikari = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            config.databaseUser?.let { username = it }
            config.databasePassword?.let { password = it }
            // SQLite allows only one writer; Postgres gets a small pool.
            maximumPoolSize = if (isSqlite) 1 else 10
            isAutoCommit = false
        }
        val database = Database.connect(HikariDataSource(hikari))
        if (isSqlite) {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        }
        transaction(database) {
            SchemaUtils.create(Users, Records, Conflicts)
        }
        return database
    }
}
