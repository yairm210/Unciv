package com.unciv.server

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

object Database {
    private const val DATABASE_URL = "jdbc:sqlite:unciv.db"

    init {
        createTables()
    }

    private fun createTables() {
        val createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL
            );
        """.trimIndent()

        val createGameSavesTable = """
            CREATE TABLE IF NOT EXISTS game_saves (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                game_id TEXT NOT NULL UNIQUE,
                game_data TEXT NOT NULL
            );
        """.trimIndent()

        executeUpdate(createUsersTable)
        executeUpdate(createGameSavesTable)
    }

    private fun executeUpdate(sql: String) {
        try {
            DriverManager.getConnection(DATABASE_URL).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(sql)
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun addUser(username: String, password: String): Boolean {
        val sql = "INSERT INTO users (username, password) VALUES (?, ?)"
        return executeUpdateWithParams(sql, username, password)
    }

    fun getUser(username: String): ResultSet? {
        val sql = "SELECT * FROM users WHERE username = ?"
        return executeQueryWithParams(sql, username)
    }

    fun addGameSave(gameId: String, gameData: String): Boolean {
        val sql = "INSERT INTO game_saves (game_id, game_data) VALUES (?, ?)"
        return executeUpdateWithParams(sql, gameId, gameData)
    }

    fun getGameSave(gameId: String): ResultSet? {
        val sql = "SELECT * FROM game_saves WHERE game_id = ?"
        return executeQueryWithParams(sql, gameId)
    }

    private fun executeUpdateWithParams(sql: String, vararg params: String): Boolean {
        try {
            DriverManager.getConnection(DATABASE_URL).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    setParams(statement, *params)
                    statement.executeUpdate()
                }
            }
            return true
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return false
    }

    private fun executeQueryWithParams(sql: String, vararg params: String): ResultSet? {
        try {
            val connection = DriverManager.getConnection(DATABASE_URL)
            val statement = connection.prepareStatement(sql)
            setParams(statement, *params)
            return statement.executeQuery()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return null
    }

    private fun setParams(statement: PreparedStatement, vararg params: String) {
        for (i in params.indices) {
            statement.setString(i + 1, params[i])
        }
    }
}
