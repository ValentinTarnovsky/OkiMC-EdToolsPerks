package com.okimc.edtoolsperks.database;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages database connections using HikariCP connection pool.
 * Supports both MySQL and SQLite backends.
 *
 * <p>All database operations are executed asynchronously using a dedicated
 * thread pool to ensure the main server thread is never blocked.</p>
 *
 * <p>Tables:</p>
 * <ul>
 *   <li>edtoolsperks_players - Player data (uuid, rolls, pity_count, animations_enabled)</li>
 *   <li>edtoolsperks_perks - Active perks (uuid, tool_type, perk_key, level)</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class DatabaseManager {

    private static final String PLAYERS_TABLE = "edtoolsperks_players";
    private static final String PERKS_TABLE = "edtoolsperks_perks";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private HikariDataSource dataSource;
    private ExecutorService executor;

    private boolean usingSqlite;
    private boolean initialized;

    // Repository instances
    private PlayerRepository playerRepository;
    private PerkRepository perkRepository;

    /**
     * Creates a new DatabaseManager instance.
     *
     * @param plugin        The main plugin instance
     * @param configManager The configuration manager
     */
    public DatabaseManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.initialized = false;
    }

    /**
     * Initializes the database connection pool and creates tables.
     *
     * @return CompletableFuture that completes when initialization is done
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create executor for async operations
                this.executor = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> {
                        Thread t = new Thread(r, "EdToolsPerks-DB");
                        t.setDaemon(true);
                        return t;
                    }
                );

                // Setup HikariCP
                setupDataSource();

                // Test connection
                try (Connection conn = getConnection()) {
                    if (conn == null || !conn.isValid(5)) {
                        plugin.getLogger().severe("Database connection test failed!");
                        return false;
                    }
                }

                // Create tables
                createTables();

                // Initialize repositories
                this.playerRepository = new PlayerRepository(this);
                this.perkRepository = new PerkRepository(this);

                this.initialized = true;
                plugin.getLogger().info("Database initialized successfully (" +
                    (usingSqlite ? "SQLite" : "MySQL") + ")");

                return true;

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
                return false;
            }
        });
    }

    /**
     * Sets up the HikariCP data source based on configuration.
     */
    private void setupDataSource() {
        HikariConfig hikariConfig = new HikariConfig();

        String dbType = configManager.getDatabaseType();
        this.usingSqlite = dbType.equalsIgnoreCase("sqlite");

        if (usingSqlite) {
            setupSqlite(hikariConfig);
        } else {
            setupMysql(hikariConfig);
        }

        // Common settings - read from config with sensible defaults
        hikariConfig.setPoolName("EdToolsPerks-Pool");
        hikariConfig.setMaximumPoolSize(
            configManager.getMainConfig().getInt("database.pool.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(
            configManager.getMainConfig().getInt("database.pool.minimum-idle", 2));
        hikariConfig.setIdleTimeout(
            configManager.getMainConfig().getLong("database.pool.idle-timeout", 600000));
        hikariConfig.setConnectionTimeout(
            configManager.getMainConfig().getLong("database.pool.connection-timeout", 30000));
        hikariConfig.setMaxLifetime(
            configManager.getMainConfig().getLong("database.pool.max-lifetime", 1800000));

        // Performance settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Configures HikariCP for SQLite.
     */
    private void setupSqlite(HikariConfig config) {
        File dbFile = new File(plugin.getDataFolder(), configManager.getSqliteFile());

        // Ensure parent directory exists
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());

        // SQLite specific settings
        config.setMaximumPoolSize(1); // SQLite doesn't support concurrent writes well
        config.setConnectionTestQuery("SELECT 1");

        // SQLite performance settings
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
    }

    /**
     * Configures HikariCP for MySQL/MariaDB.
     */
    private void setupMysql(HikariConfig config) {
        String host = configManager.getDatabaseHost();
        int port = configManager.getDatabasePort();
        String database = configManager.getDatabaseName();
        String username = configManager.getDatabaseUsername();
        String password = configManager.getDatabasePassword();

        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
            "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8");
        config.setUsername(username);
        config.setPassword(password);

        // MySQL specific settings
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
    }

    /**
     * Creates the required database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        String playersTable;
        String perksTable;

        if (usingSqlite) {
            playersTable = """
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid TEXT PRIMARY KEY,
                    rolls INTEGER DEFAULT 0,
                    pity_count INTEGER DEFAULT 0,
                    animations_enabled INTEGER DEFAULT 1
                )
                """.formatted(PLAYERS_TABLE);

            perksTable = """
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid TEXT NOT NULL,
                    tool_type TEXT NOT NULL,
                    perk_key TEXT NOT NULL,
                    level INTEGER DEFAULT 1,
                    PRIMARY KEY (player_uuid, tool_type)
                )
                """.formatted(PERKS_TABLE);
        } else {
            playersTable = """
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    rolls INT DEFAULT 0,
                    pity_count INT DEFAULT 0,
                    animations_enabled TINYINT(1) DEFAULT 1
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(PLAYERS_TABLE);

            perksTable = """
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid VARCHAR(36) NOT NULL,
                    tool_type VARCHAR(32) NOT NULL,
                    perk_key VARCHAR(64) NOT NULL,
                    level INT DEFAULT 1,
                    PRIMARY KEY (player_uuid, tool_type),
                    INDEX idx_player (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """.formatted(PERKS_TABLE);
        }

        try (Connection conn = getConnection();
             PreparedStatement ps1 = conn.prepareStatement(playersTable);
             PreparedStatement ps2 = conn.prepareStatement(perksTable)) {

            ps1.executeUpdate();
            ps2.executeUpdate();

            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] Database tables created/verified.");
            }
        }
    }

    /**
     * Gets a connection from the pool.
     *
     * @return A database connection
     * @throws SQLException If unable to get connection
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not available!");
        }
        return dataSource.getConnection();
    }

    /**
     * Executes an async database operation.
     *
     * @param operation The operation to execute
     * @param <T>       The return type
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        if (executor == null || executor.isShutdown()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Database executor is not available!")
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                return operation.execute(conn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed!", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Executes an async database operation that doesn't return a value.
     *
     * @param operation The operation to execute
     * @return CompletableFuture that completes when done
     */
    public CompletableFuture<Void> executeAsyncVoid(DatabaseVoidOperation operation) {
        if (executor == null || executor.isShutdown()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Database executor is not available!")
            );
        }

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                operation.execute(conn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed!", e);
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * Shuts down the database connection pool gracefully.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down database...");

        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close data source
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }

        this.initialized = false;
        plugin.getLogger().info("Database shutdown complete.");
    }

    // ==================== Getters ====================

    /**
     * Gets the player repository.
     *
     * @return The PlayerRepository instance
     */
    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    /**
     * Gets the perk repository.
     *
     * @return The PerkRepository instance
     */
    public PerkRepository getPerkRepository() {
        return perkRepository;
    }

    /**
     * Checks if the database is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if using SQLite backend.
     *
     * @return true if SQLite
     */
    public boolean isUsingSqlite() {
        return usingSqlite;
    }

    /**
     * Gets the players table name.
     *
     * @return The table name
     */
    public String getPlayersTable() {
        return PLAYERS_TABLE;
    }

    /**
     * Gets the perks table name.
     *
     * @return The table name
     */
    public String getPerksTable() {
        return PERKS_TABLE;
    }

    /**
     * Gets the plugin instance.
     *
     * @return The plugin
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the config manager.
     *
     * @return The ConfigManager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    // ==================== Functional Interfaces ====================

    /**
     * Functional interface for database operations that return a value.
     *
     * @param <T> The return type
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    /**
     * Functional interface for database operations that don't return a value.
     */
    @FunctionalInterface
    public interface DatabaseVoidOperation {
        void execute(Connection connection) throws SQLException;
    }
}
