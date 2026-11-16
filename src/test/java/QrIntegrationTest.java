import com.google.zxing.ReaderException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.*;
import java.util.concurrent.*;

/** Run manually against the configured MySQL server; removes only its own test student. */
public final class QrIntegrationTest {
    public static void main(String[] args) throws Exception {
        String roll = "QRTEST_" + Long.toHexString(System.nanoTime());
        BufferedImage qr = QrCodeService.generate(roll);
        if (!roll.equals(QrCodeService.decode(qr))) throw new AssertionError("QR round trip");
        try {
            QrCodeService.decode(new BufferedImage(360, 360, BufferedImage.TYPE_INT_RGB));
            throw new AssertionError("Blank image accepted");
        } catch (ReaderException expected) { }
        try { QrCodeService.decode(null); throw new AssertionError("Invalid image accepted"); }
        catch (IOException expected) { }
        AttendanceRepository repo = new AttendanceRepository();
        try {
            try { repo.markPresentFromQr(roll); throw new AssertionError("Unknown student accepted"); }
            catch (SQLException expected) { if (!"45000".equals(expected.getSQLState())) throw expected; }
            repo.add(roll, "QR integration test", "REGISTERED");
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch gate = new CountDownLatch(1);
                Callable<Boolean> scan = () -> { gate.await(); return new AttendanceRepository().markPresentFromQr(QrCodeService.decode(qr)); };
                Future<Boolean> first = pool.submit(scan), second = pool.submit(scan);
                gate.countDown();
                if (first.get(20, TimeUnit.SECONDS) == second.get(20, TimeUnit.SECONDS))
                    throw new AssertionError("Concurrent scans must create exactly one attendance");
            } finally { pool.shutdownNow(); }
            if (repo.markPresentFromQr(roll)) throw new AssertionError("Duplicate QR attendance");
            if (!"QR integration test".equals(repo.findStudentName(roll))) throw new AssertionError("Student name changed");
            long count = repo.findAll().stream().filter(r -> r.rollNumber().equals(roll) && r.status().equals("PRESENT")).count();
            if (count != 1) throw new AssertionError("Expected one PRESENT record, got " + count);
            System.out.println("PASS: QR round trip, invalid images, unknown student, MySQL persistence and concurrent duplicate scans.");
        } finally {
            try (Connection c = Database.connect()) {
                for (String table : new String[]{"attendance", "students"}) {
                    try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table + " WHERE roll_number=?")) {
                        s.setString(1, roll); s.executeUpdate();
                    }
                }
            }
        }
    }
}
