package com.connorhansen.publiclandmap.api.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.slf4j.LoggerFactory
import java.sql.Connection

class DatabaseConfig private constructor(private val dataSource: HikariDataSource) {

    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    fun getConnection(): Connection = dataSource.connection

    fun isHealthy(): Boolean {
        return try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { rs ->
                        rs.next()
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Database health check failed", e)
            false
        }
    }

    fun close() {
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

        fun init(config: ApplicationConfig): DatabaseConfig {
            val url = config.property("database.url").getString()
            val user = config.property("database.user").getString()
            val password = config.property("database.password").getString()
            val maxPoolSize = config.property("database.maxPoolSize").getString().toInt()
            val connectionTimeoutMs = config.propertyOrNull("database.connectionTimeoutMs")?.getString()?.toLong() ?: 10_000L

            logger.info("Initializing database connection pool: url={}, user={}, maxPoolSize={}", url, user, maxPoolSize)

            val hikariConfig = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = maxPoolSize
                minimumIdle = 2
                idleTimeout = 60_000
                connectionTimeout = connectionTimeoutMs
                // Start without a live database; /health then reports "degraded" instead of the server failing to boot
                initializationFailTimeout = -1
                maxLifetime = 1_800_000
                isAutoCommit = true

                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                addDataSourceProperty("useServerPrepStmts", "true")
            }

            val dataSource = HikariDataSource(hikariConfig)
            logger.info("Database connection pool initialized successfully")
            return DatabaseConfig(dataSource)
        }
    }
}
