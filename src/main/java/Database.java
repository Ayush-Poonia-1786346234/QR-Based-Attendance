import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private Database() { }

    public static Connection connect() throws SQLException {
        Properties settings = new Properties();
        try (InputStream defaults = Database.class.getResourceAsStream("/database.properties")) {
            if (defaults == null) throw new IOException("Missing database.properties resource");
            settings.load(defaults);
            Path local = Path.of("config", "database.properties");
            if (Files.exists(local)) {
                try (InputStream input = Files.newInputStream(local)) { settings.load(input); }
            }
        } catch (IOException exception) {
            throw new SQLException("Cannot load database configuration", exception);
        }
        return DriverManager.getConnection(
                value(settings, "db.url", "DB_URL"),
                value(settings, "db.user", "DB_USER"),
                value(settings, "db.password", "DB_PASSWORD"));
    }

    private static String value(Properties settings, String key, String environment) {
        String override = System.getenv(environment);
        return override == null ? settings.getProperty(key) : override;
    }
}
