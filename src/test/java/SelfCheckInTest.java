import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.concurrent.*;

public final class SelfCheckInTest {
    static final HttpClient CLIENT = HttpClient.newHttpClient();
    public static void main(String[] args) throws Exception {
        String roll = "SELFTEST_" + Long.toHexString(System.nanoTime()).toUpperCase();
        String secondRoll = roll + "B";
        AttendanceRepository repo = new AttendanceRepository();
        try (MobileAttendanceServer server = new MobileAttendanceServer(repo, m -> {}, "127.0.0.1", 0)) {
            String url = server.classUrl(System.currentTimeMillis());
            var page = request(url, null);
            check(page.statusCode() == 200 && page.body().contains("name=\"roll\"") && page.body().contains("name=\"name\""), "Form fields missing");
            check(repo.findStudentName(roll) == null, "GET registered a student");
            check(request(url, "roll=&name=").statusCode() == 400, "Blank details accepted");
            check(request(url, "roll=" + "X".repeat(31) + "&name=Student").statusCode() == 400, "Oversized roll accepted");
            String body = "roll=" + roll.toLowerCase() + "&name=" + URLEncoder.encode("Student <Test>", StandardCharsets.UTF_8);
            check(request(url + "x", body).statusCode() == 400, "Tampered class token accepted");
            check(url.equals(server.classUrl(System.currentTimeMillis() + 86_400_000)), "Class QR changed with time");
            check(request(server.classUrl(System.currentTimeMillis() - 86_400_000), null).statusCode() == 200, "Class QR expired with time");
            check(request(server.urlFor("class", System.currentTimeMillis()).replace("/a?", "/check-in?"), body).statusCode() == 400, "Student token accepted as class token");
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                String fresh = server.classUrl(System.currentTimeMillis());
                Future<HttpResponse<String>> a = pool.submit(() -> request(fresh, body));
                Future<HttpResponse<String>> b = pool.submit(() -> request(fresh, "roll=" + secondRoll + "&name=Second"));
                var ra = a.get(15, TimeUnit.SECONDS); var rb = b.get(15, TimeUnit.SECONDS);
                check((ra.statusCode() == 200 && rb.statusCode() == 400) || (ra.statusCode() == 400 && rb.statusCode() == 200), "Exactly one student must succeed: " + ra.body() + rb.body());
                check(ra.body().contains("Attendance saved") != rb.body().contains("Attendance saved"), "Expected exactly one new attendance");
                check(server.isClassQrUsed(), "Successful submission did not consume QR");
                check((repo.findStudentName(roll) == null) != (repo.findStudentName(secondRoll) == null), "Both students were registered with one QR");
                check(request(fresh, null).statusCode() == 400, "Used QR reopened form");
                check(request(fresh, body).statusCode() == 400, "Used QR accepted another submission");
            } finally { pool.shutdownNow(); }
            server.regenerateClassQr();
            check(!server.isClassQrUsed(), "New QR is already consumed");
            var registered = request(server.classUrl(System.currentTimeMillis()), body);
            check(registered.statusCode() == 200 && registered.body().contains("Student &lt;Test&gt;"), "New QR failed or name unescaped");
            check(!registered.body().contains(">Next student</a>"), "Success page offers reused form");
            check("Student <Test>".equals(repo.findStudentName(roll)), "Student was not registered");
            server.regenerateClassQr();
            check(request(server.classUrl(System.currentTimeMillis()), "roll=" + roll + "&name=Wrong").statusCode() == 400, "Existing name overwritten");
            check("Student <Test>".equals(repo.findStudentName(roll)), "Name changed");
            check(!server.isClassQrUsed(), "Invalid submission consumed QR");
            check(repo.findAll().stream().filter(r -> r.rollNumber().equals(roll) && r.status().equals("PRESENT")).count() == 1, "Duplicate record");
            check(request(server.classUrl(System.currentTimeMillis()), body).statusCode() == 200, "Retry after invalid submission failed");
            check(server.isClassQrUsed(), "Already-present submission must consume QR too");
            String previous = server.classUrl(System.currentTimeMillis());
            server.regenerateClassQr();
            String replacement = server.classUrl(System.currentTimeMillis());
            check(!previous.equals(replacement), "Manual generation did not change QR");
            check(request(previous, null).statusCode() == 400, "Replaced QR still opens form");
            check(request(previous, body).statusCode() == 400, "Replaced QR still accepts submissions");
            check(request(replacement, null).statusCode() == 200, "Replacement QR does not open form");
            System.out.println("PASS: class form, validation, manual replacement, no expiry, single-use enforcement across two concurrent students, signature isolation, self-registration, daily deduplication, name protection and escaped output.");
        } finally {
            try (Connection c = Database.connect()) {
                for (String table : new String[]{"attendance", "students"}) {
                    try (PreparedStatement s = c.prepareStatement("DELETE FROM " + table + " WHERE roll_number IN (?, ?)")) { s.setString(1, roll); s.setString(2, secondRoll); s.executeUpdate(); }
                }
            }
        }
    }
    static HttpResponse<String> request(String url, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(10));
        if (body != null) builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
