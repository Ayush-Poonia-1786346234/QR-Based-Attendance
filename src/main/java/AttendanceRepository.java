import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class AttendanceRepository {
    public record AttendanceRecord(int id, String rollNumber, String studentName, String status) { }

    public List<AttendanceRecord> findAll() throws SQLException {
        List<AttendanceRecord> records = new ArrayList<>();
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT a.attendance_id, a.roll_number, s.student_name, a.attendance_status "
                     + "FROM attendance a JOIN students s ON s.roll_number=a.roll_number ORDER BY a.attendance_id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) records.add(new AttendanceRecord(rows.getInt(1), rows.getString(2),
                    rows.getString(3), rows.getString(4)));
        }
        return records;
    }

    public void add(String rollNumber, String name, String status) throws SQLException {
        save(null, rollNumber, name, status);
    }

    public void update(int id, String rollNumber, String name) throws SQLException {
        save(id, rollNumber, name, null);
    }

    public String findStudentName(String roll) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement s = c.prepareStatement("SELECT student_name FROM students WHERE roll_number=?")) {
            s.setString(1, roll);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    // Lock the student so simultaneous QR scans cannot insert duplicate daily attendance.
    public boolean markPresentFromQr(String roll) throws SQLException {
        return markPresent(roll, null);
    }

    public boolean selfCheckIn(String roll, String name) throws SQLException {
        if (roll == null || name == null || roll.isBlank() || name.isBlank()
                || roll.length() > 30 || name.length() > 100
                || roll.chars().anyMatch(Character::isISOControl) || name.chars().anyMatch(Character::isISOControl))
            throw new SQLException("Enter a roll number (up to 30 characters) and name (up to 100 characters).", "45000");
        return markPresent(roll, name);
    }

    private boolean markPresent(String roll, String name) throws SQLException {
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                if (name != null) {
                    try (PreparedStatement s = c.prepareStatement("INSERT INTO students (roll_number, student_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE roll_number=roll_number")) {
                        s.setString(1, roll); s.setString(2, name); s.executeUpdate();
                    }
                }
                try (PreparedStatement s = c.prepareStatement("SELECT student_name FROM students WHERE roll_number=? FOR UPDATE")) {
                    s.setString(1, roll);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new SQLException("Student not found. Register the student first.", "45000");
                        if (name != null && !name.equalsIgnoreCase(r.getString(1).trim()))
                            throw new SQLException("This roll number is registered with a different name. Check your details or ask the teacher.", "45000");
                    }
                }
                boolean exists;
                try (PreparedStatement s = c.prepareStatement("SELECT attendance_id FROM attendance WHERE roll_number=? AND attendance_date=CURRENT_DATE AND attendance_status='PRESENT' LIMIT 1")) {
                    s.setString(1, roll);
                    try (ResultSet r = s.executeQuery()) { exists = r.next(); }
                }
                if (!exists) {
                    try (PreparedStatement s = c.prepareStatement("INSERT INTO attendance (roll_number, attendance_date, attendance_time, attendance_status) VALUES (?, CURRENT_DATE, CURRENT_TIME, 'PRESENT')")) {
                        s.setString(1, roll);
                        s.executeUpdate();
                    }
                }
                c.commit();
                return !exists;
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }

    private void save(Integer id, String rollNumber, String name, String status) throws SQLException {
        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement student = connection.prepareStatement(
                        "INSERT INTO students (roll_number, student_name) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE student_name=?")) {
                    student.setString(1, rollNumber);
                    student.setString(2, name);
                    student.setString(3, name);
                    student.executeUpdate();
                }
                String sql = id == null
                        ? "INSERT INTO attendance (roll_number, attendance_date, attendance_time, attendance_status) VALUES (?, CURRENT_DATE, CURRENT_TIME, ?)"
                        : "UPDATE attendance SET roll_number=? WHERE attendance_id=?";
                try (PreparedStatement attendance = connection.prepareStatement(sql)) {
                    attendance.setString(1, rollNumber);
                    if (id == null) attendance.setString(2, status);
                    else attendance.setInt(2, id);
                    if (attendance.executeUpdate() == 0) throw new SQLException("Record no longer exists");
                }
                connection.commit();
            } catch (SQLException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
}
