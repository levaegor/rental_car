package modules;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    public static String getBotName() {
        return Dotenv.load().get("BOT_NAME");
    }

    public static String getBotToken() {
        return Dotenv.load().get("BOT_TOKEN");
    }

    public static String getDatabaseHost() {
        return Dotenv.load().get("DB_HOST");
    }

    public static String getDatabasePort() {
        return Dotenv.load().get("DB_PORT");
    }

    public static String getDatabaseName() {
        return Dotenv.load().get("DB_NAME");
    }

    public static String getDatabaseUser() {
        return Dotenv.load().get("DB_USER");
    }

    public static String getDatabasePassword() {
        return Dotenv.load().get("DB_PASSWORD");
    }

    public static String getAdminPassword() {
        return Dotenv.load().get("ADMIN_PASSWORD");
    }
}
