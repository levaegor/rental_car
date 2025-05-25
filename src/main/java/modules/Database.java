package modules;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database {
    private static final Logger LOGGER = Logger.getLogger(Database.class.getName());

    private static final String DB_HOST = Config.getDatabaseHost();
    private static final String DB_PORT = Config.getDatabasePort();
    private static final String DB_NAME = Config.getDatabaseName();
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    private static final String DB_USER = Config.getDatabaseUser();
    private static final String DB_PASSWORD = Config.getDatabasePassword();

    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                LOGGER.info("Database connection established successfully.");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to connect to the database", e);
                throw e; // Re-throw the exception for further handling
            }
        }
        return connection;
    }
}