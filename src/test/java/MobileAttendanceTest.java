import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;

public final class MobileAttendanceTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    public static void main(String[] args) throws Exception {
        String roll = "MOBTEST_" + Long.toHexString(System.nanoTime());
        AttendanceRepository repo = new AttendanceRepository();
        AtomicInteger callbacks = new AtomicInteger();
        try (MobileAttendanceServer server = new MobileAttendanceServer(repo, m -> callbacks.incrementAndGet(), "127.0.0.1", 0)) {
            QrCodeService.setMobileServer(server);
            repo.add(roll, "Mobile <test>", "REGISTERED");
            long now = System.currentTimeMillis();
            for (int size : new int[]{160, 360}) {
                var qr = QrCodeService.generate(roll, size, now);
                check(roll.equals(QrCodeService.decode(qr, now)), "Desktop cannot read mobile QR");
                int w = qr.getWidth(), h = qr.getHeight();
                String raw = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, qr.getRGB(0,0,w,h,null,0,w))))).getText();
                check(raw.startsWith(server.baseUrl() + "/a?c="), "QR is not a mobile URL");
            }
            String url = server.urlFor(roll, System.currentTimeMillis());
            var page = request(url, "GET");
            check(page.statusCode() == 200 && page.body().contains("Mark me present"), "Phone page missing");
            check(page.body().contains("Mobile &lt;test&gt;"), "Name not escaped");
            check(presentCount(repo, roll) == 0, "GET marked attendance");
            check(request(url + "x", "POST").statusCode() == 400, "Altered token accepted");
            check(request(server.urlFor(roll, System.currentTimeMillis() - 86_400_000), "GET").statusCode() == 200, "Link must not expire with time");
            check(presentCount(repo, roll) == 0, "Invalid request saved attendance");
            url = server.urlFor(roll, System.currentTimeMillis());
            check(request(url, "POST").body().contains("Attendance saved"), "Mobile attendance not saved");
            check(request(url, "POST").body().contains("Already present today"), "Duplicate handling failed");
            check(presentCount(repo, roll) == 1 && callbacks.get() == 2, "Database/callback mismatch");
            check(request(server.baseUrl(), "GET").body().contains("Mobile attendance is ready"), "Connection page missing");
            check(request(url, "DELETE").statusCode() == 405, "Unsupported method allowed");
            System.out.println("PASS: phone URL QR decoding, confirmation page, safe GET, signed tokens, no time limit, POST persistence, duplicates and desktop refresh callback.");
        } finally {
            QrCodeService.setMobileServer(null);
            try (Connection c = Database.connect()) {
                for (String table : new String[]{"attendance", "students"}) {
                    try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table + " WHERE roll_number=?")) {
                        s.setString(1, roll); s.executeUpdate();
                    }
                }
            }
        }
    }
    private static long presentCount(AttendanceRepository repo, String roll) throws Exception {
        return repo.findAll().stream().filter(r -> r.rollNumber().equals(roll) && r.status().equals("PRESENT")).count();
    }
    private static HttpResponse<String> request(String url, String method) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
