package modules;

import java.sql.*;
import java.util.logging.Logger;
import java.util.*; // added for List/Map etc.

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
        if (connection == null || !connection.isValid(2)) {
            try {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            } catch (SQLException e) {
                throw e; // Re-throw the exception for further handling
            }
        }
        return connection;
    }

    // small functional interface to allow retries
    // Сделано public static, чтобы другие пакеты могли передавать лямбды в withUserRetries(...)
    public static interface SQLSupplier<T> {
        T get() throws SQLException;
    }

    // retry helper: retries transient/recoverable SQL exceptions
    private static <T> T runWithRetries(SQLSupplier<T> action) throws SQLException {
        final int MAX_ATTEMPTS = 3;
        long backoff = 150; // ms
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (SQLTransientException | SQLRecoverableException e) {
                LOGGER.warning("Transient DB error on attempt " + attempt + ": " + e.getMessage());
                if (attempt == MAX_ATTEMPTS) throw e;
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff *= 2;
            }
        }
        // should not reach here
        throw new SQLException("Failed after retries");
    }

    /**
     * Унифицированный helper для вызовов из хэндлеров.
     * Попробовать action до 3 раз; между попытками посылать пользователю короткое сообщение прогресса.
     * Если после 3 попыток всё ещё SQLException — уведомить пользователя и вернуть null.
     */
    public static <T> T withUserRetries(org.bot.MessageInteraction mi, long chatId, SQLSupplier<T> action) {
        final int MAX_ATTEMPTS = 3;
        long backoff = 150L;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (SQLException e) {
                LOGGER.warning("DB error (attempt " + attempt + "): " + e.getClass().getSimpleName() + " - " + e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try { mi.sendMessage(chatId, "Work in progress... ⏳"); } catch (Exception ignored) {}
                    try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                    backoff *= 2;
                    continue;
                } else {
                    try { mi.sendMessage(chatId, "Database error occurred. Please try again later."); } catch (Exception ignored) {}
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean isUser(String login) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "select 1 from Users where login = ? limit 1";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, login);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    return resultSet.next();
                }
            }
        });
    }

    public static boolean verifyPassword(String login, String password) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "SELECT password FROM Users WHERE login = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, login);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        String dbPass = resultSet.getString("password");
                        return dbPass != null && dbPass.equals(password);
                    }
                    return false;
                }
            }
        });
    }

    public static void createUserWithPassword(String login, String password, String email, String phone, String driverLicense) throws SQLException {
        runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "INSERT INTO Users (login, phone_number, license_id, email, password) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, login);
                preparedStatement.setString(2, phone);
                preparedStatement.setString(3, driverLicense);
                preparedStatement.setString(4, email);
                preparedStatement.setString(5, password);
                preparedStatement.executeUpdate();
            }
            return null;
        });
    }

    public static boolean isEmailUsed(String email) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "SELECT 1 FROM Users WHERE email = ? LIMIT 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public static boolean isPhoneUsed(String phone) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "SELECT 1 FROM Users WHERE phone_number = ? LIMIT 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, phone);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public static boolean isLicenseUsed(String driverLicense) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "SELECT 1 FROM Users WHERE license_id = ? LIMIT 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, driverLicense);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    public static boolean isAdmin(String login) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "SELECT isAdmin FROM Users WHERE login = ? LIMIT 1";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, login);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    int val = rs.getInt("isAdmin");
                    if (rs.wasNull()) return false;
                    return val != 0;
                }
            }
        });
    }

    // New: set isAdmin flag for a login (returns true if a row was updated)
    public static boolean setAdminStatus(String login, boolean isAdmin) throws SQLException {
        return runWithRetries(() -> {
            Connection connection = getConnection();
            String sql = "UPDATE Users SET isAdmin = ? WHERE login = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, isAdmin ? 1 : 0);
                ps.setString(2, login);
                int updated = ps.executeUpdate();
                return updated > 0;
            }
        });
    }

    // New: list branches that have at least one available car (status_id = 1)
    public static List<Map<String,Object>> getBranchesWithAvailableCars() throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT b.id, b.city, b.street, b.building_number " +
                    "FROM Branch b " +
                    "INNER JOIN Cars c ON c.branch_id = b.id " +
                    "WHERE c.status_id = 1 " +
                    "GROUP BY b.id, b.city, b.street, b.building_number " +
                    "ORDER BY b.id";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("city", rs.getString("city"));
                    m.put("street", rs.getString("street"));
                    m.put("building_number", rs.getInt("building_number"));
                    out.add(m);
                }
            }
            return out;
        });
    }

    // New: list all branches (used for return branch selection)
    public static List<Map<String,Object>> getAllBranches() throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT id, city, street, building_number FROM Branch ORDER BY id";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("city", rs.getString("city"));
                    m.put("street", rs.getString("street"));
                    m.put("building_number", rs.getInt("building_number"));
                    out.add(m);
                }
            }
            return out;
        });
    }

    // New: cars available in a branch (status_id = 1), returning maps (no DTO)
    public static List<Map<String,Object>> getCarsAvailableInBranch(int branchId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT c.id, c.name, c.release_year, t.type_name, c.branch_id " +
                    "FROM Cars c " +
                    "INNER JOIN CarType t ON t.id = c.type_id " +
                    "WHERE c.branch_id = ? AND c.status_id = 1 " +
                    "ORDER BY c.id";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> m = new HashMap<>();
                        m.put("id", rs.getInt("id"));
                        m.put("name", rs.getString("name"));
                        Object ry = rs.getObject("release_year");
                        m.put("release_year", ry == null ? null : rs.getInt("release_year"));
                        m.put("type_name", rs.getString("type_name"));
                        m.put("branch_id", rs.getInt("branch_id"));
                        out.add(m);
                    }
                }
            }
            return out;
        });
    }

    // New: get single car by id (returns map or null)
    public static Map<String,Object> getCarById(int carId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT c.id, c.name, c.release_year, t.type_name, c.branch_id, c.status_id, cs.status_name " +
                    "FROM Cars c " +
                    "INNER JOIN CarType t ON t.id = c.type_id " +
                    "LEFT JOIN CarStatus cs ON cs.id = c.status_id " +
                    "WHERE c.id = ? LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, carId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("name", rs.getString("name"));
                    Object ry = rs.getObject("release_year");
                    m.put("release_year", ry == null ? null : rs.getInt("release_year"));
                    m.put("type_name", rs.getString("type_name"));
                    m.put("branch_id", rs.getInt("branch_id"));
                    m.put("status_id", rs.getInt("status_id"));
                    m.put("status_name", rs.getString("status_name")); // <-- добавлено чтение названия статуса
                    return m;
                }
            }
        });
    }

    // New: get branch by id (returns map or null)
    public static Map<String,Object> getBranchById(int branchId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT id, city, street, building_number FROM Branch WHERE id = ? LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("city", rs.getString("city"));
                    m.put("street", rs.getString("street"));
                    m.put("building_number", rs.getInt("building_number"));
                    return m;
                }
            }
        });
    }

    // New: map login -> user id (used when inserting rental)
    public static Integer getUserIdByLogin(String login) throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT id FROM Users WHERE login = ? LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, login);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return rs.getInt("id");
                }
            }
        });
    }

    // New: create rental (dates provided as java.sql.Date) and set car status to 2 in same transaction
    public static boolean createRental(int carId, int userId, int startBranchId, int endBranchId, java.sql.Date startDate, java.sql.Date endDate) throws SQLException {
        return runWithRetries(() -> {
            Connection conn = getConnection();
            boolean oldAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String insertSql = "INSERT INTO Rentals (car_id, user_id, start_branch_id, end_branch_id, start_date, end_date) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                    psInsert.setInt(1, carId);
                    psInsert.setInt(2, userId);
                    psInsert.setInt(3, startBranchId);
                    psInsert.setInt(4, endBranchId);
                    psInsert.setDate(5, startDate);
                    psInsert.setDate(6, endDate);
                    int inserted = psInsert.executeUpdate();
                    if (inserted <= 0) {
                        conn.rollback();
                        return false;
                    }
                }

                String updateCarSql = "UPDATE Cars SET status_id = ? WHERE id = ?";
                try (PreparedStatement psUpdate = conn.prepareStatement(updateCarSql)) {
                    psUpdate.setInt(1, 2); // booked
                    psUpdate.setInt(2, carId);
                    int updated = psUpdate.executeUpdate();
                    if (updated <= 0) {
                        conn.rollback();
                        return false;
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (Exception ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
            }
        });
    }

    // New: list active rentals for a given login using the RentalDetails view
    public static List<Map<String,Object>> getActiveRentalsByLogin(String login) throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT RentalID, `Car Name` AS car_name, `Start Date` AS start_date, `End Date` AS end_date, " +
                         "`Start Branch` AS start_branch_addr, `End Branch` AS end_branch_addr " +
                         "FROM RentalDetails WHERE Login = ? ORDER BY RentalID";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, login);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> m = new HashMap<>();
                        m.put("rental_id", rs.getInt("RentalID"));
                        m.put("car_name", rs.getString("car_name"));
                        m.put("start_date", rs.getDate("start_date")); // java.sql.Date
                        m.put("end_date", rs.getDate("end_date"));
                        m.put("start_branch_addr", rs.getString("start_branch_addr"));
                        m.put("end_branch_addr", rs.getString("end_branch_addr"));
                        out.add(m);
                    }
                }
            }
            return out;
        });
    }

    // New: get single rental by id (returns map or null)
    public static Map<String,Object> getRentalById(int rentalId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT r.id as rental_id, r.car_id, c.name as car_name, r.start_date, r.end_date, " +
                         "b1.id as start_branch_id, CONCAT(b1.city, ', ', b1.street, ', ', b1.building_number) as start_branch_addr, " +
                         "b2.id as end_branch_id, CONCAT(b2.city, ', ', b2.street, ', ', b2.building_number) as end_branch_addr " +
                         "FROM Rentals r " +
                         "INNER JOIN Cars c ON r.car_id = c.id " +
                         "INNER JOIN Branch b1 ON r.start_branch_id = b1.id " +
                         "INNER JOIN Branch b2 ON r.end_branch_id = b2.id " +
                         "WHERE r.id = ? LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, rentalId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    Map<String,Object> m = new HashMap<>();
                    m.put("rental_id", rs.getInt("rental_id"));
                    m.put("car_id", rs.getInt("car_id"));
                    m.put("car_name", rs.getString("car_name"));
                    m.put("start_date", rs.getDate("start_date"));
                    m.put("end_date", rs.getDate("end_date"));
                    m.put("start_branch_id", rs.getInt("start_branch_id"));
                    m.put("start_branch_addr", rs.getString("start_branch_addr"));
                    m.put("end_branch_id", rs.getInt("end_branch_id"));
                    m.put("end_branch_addr", rs.getString("end_branch_addr"));
                    return m;
                }
            }
        });
    }

    // New: delete rental by id and restore car status to 1 in same transaction
    public static boolean deleteRentalById(int rentalId) throws SQLException {
        return runWithRetries(() -> {
            Connection conn = getConnection();
            boolean oldAuto = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                // get car id
                Integer carId = null;
                String select = "SELECT car_id FROM Rentals WHERE id = ? LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(select)) {
                    ps.setInt(1, rentalId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) carId = rs.getInt("car_id");
                    }
                }
                if (carId == null) { conn.rollback(); return false; }

                // lock car row and read current status
                Integer currentStatus = null;
                String selStatus = "SELECT status_id FROM Cars WHERE id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(selStatus)) {
                    ps.setInt(1, carId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            currentStatus = rs.getInt("status_id");
                            if (rs.wasNull()) currentStatus = null;
                        }
                    }
                }

                // delete rental
                String del = "DELETE FROM Rentals WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(del)) {
                    ps.setInt(1, rentalId);
                    int d = ps.executeUpdate();
                    if (d <= 0) { conn.rollback(); return false; }
                }

                // restore car status = 1 only if current status is NOT 4
                if (currentStatus == null || currentStatus != 4) {
                    String upd = "UPDATE Cars SET status_id = ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(upd)) {
                        ps.setInt(1, 1);
                        ps.setInt(2, carId);
                        int u = ps.executeUpdate();
                        if (u <= 0) { conn.rollback(); return false; }
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (Exception ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(oldAuto); } catch (Exception ignored) {}
            }
        });
    }

    // New: update rental start date
    public static boolean updateRentalStartDate(int rentalId, java.sql.Date newStartDate) throws SQLException {
        return runWithRetries(() -> {
            String sql = "UPDATE Rentals SET start_date = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, newStartDate);
                ps.setInt(2, rentalId);
                int u = ps.executeUpdate();
                return u > 0;
            }
        });
    }

    // New: update rental end branch and end date
    public static boolean updateRentalReturnBranchAndDate(int rentalId, int newEndBranchId, java.sql.Date newEndDate) throws SQLException {
        return runWithRetries(() -> {
            String sql = "UPDATE Rentals SET end_branch_id = ?, end_date = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, newEndBranchId);
                ps.setDate(2, newEndDate);
                ps.setInt(3, rentalId);
                int u = ps.executeUpdate();
                return u > 0;
            }
        });
    }

    // New: list ALL cars (no pagination) - used for in-memory pagination in AdminHandler
    public static List<Map<String,Object>> listAllCars() throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT c.id, c.name, c.release_year, c.branch_id, b.city, b.street, b.building_number, c.status_id, cs.status_name, t.type_name " +
                    "FROM Cars c " +
                    "LEFT JOIN Branch b ON b.id = c.branch_id " +
                    "LEFT JOIN CarStatus cs ON cs.id = c.status_id " +
                    "LEFT JOIN CarType t ON t.id = c.type_id " +
                    "ORDER BY c.id";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("name", rs.getString("name"));
                    Object ry = rs.getObject("release_year");
                    m.put("release_year", ry == null ? null : rs.getInt("release_year"));
                    m.put("branch_id", rs.getInt("branch_id"));
                    m.put("branch_city", rs.getString("city"));
                    m.put("branch_street", rs.getString("street"));
                    m.put("branch_building", rs.getObject("building_number"));
                    m.put("status_id", rs.getInt("status_id"));
                    m.put("status_name", rs.getString("status_name"));
                    m.put("type_name", rs.getString("type_name"));
                    out.add(m);
                }
            }
            return out;
        });
    }

    // New: delete car by id
    public static boolean deleteCarById(int carId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "DELETE FROM Cars WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, carId);
                int u = ps.executeUpdate();
                return u > 0;
            }
        });
    }

    // New: move car to another branch (update branch_id)
    public static boolean moveCarToBranch(int carId, int branchId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "UPDATE Cars SET branch_id = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setInt(2, carId);
                int u = ps.executeUpdate();
                return u > 0;
            }
        });
    }

    // New: list all car statuses
    public static List<Map<String,Object>> listCarStatuses() throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT id, status_name FROM CarStatus ORDER BY id";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("status_name", rs.getString("status_name"));
                    out.add(m);
                }
            }
            return out;
        });
    }

    // New: update car status
    public static boolean updateCarStatus(int carId, int statusId) throws SQLException {
        return runWithRetries(() -> {
            String sql = "UPDATE Cars SET status_id = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                ps.setInt(2, carId);
                int u = ps.executeUpdate();
                return u > 0;
            }
        });
    }

    // New: list ALL rentals from RentalDetails view (no pagination) - used for in-memory pagination in AdminHandler
    public static List<Map<String,Object>> listAllRentals() throws SQLException {
        return runWithRetries(() -> {
            String sql = "SELECT RentalID, `Car Name` AS car_name, Login AS login, `Start Date` AS start_date, `End Date` AS end_date, `Start Branch` AS start_branch_addr, `End Branch` AS end_branch_addr " +
                         "FROM RentalDetails ORDER BY RentalID";
            List<Map<String,Object>> out = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("rental_id", rs.getInt("RentalID"));
                    m.put("car_name", rs.getString("car_name"));
                    m.put("login", rs.getString("login"));
                    m.put("start_date", rs.getDate("start_date"));
                    m.put("end_date", rs.getDate("end_date"));
                    m.put("start_branch_addr", rs.getString("start_branch_addr"));
                    m.put("end_branch_addr", rs.getString("end_branch_addr"));
                    out.add(m);
                }
            }
            return out;
        });
    }
}
