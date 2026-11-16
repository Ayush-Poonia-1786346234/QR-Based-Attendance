import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Local Wi-Fi attendance page. QR links are signed and replaced manually. */
public final class MobileAttendanceServer implements AutoCloseable {
    private final AttendanceRepository repository;
    private final Consumer<String> onAttendance;
    private final byte[] secret = new byte[32];
    private final HttpServer server;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private volatile String baseUrl;
    private volatile String classToken = UUID.randomUUID().toString();
    private boolean classQrUsed;

    public MobileAttendanceServer(AttendanceRepository repository, Consumer<String> onAttendance,
                                  String host, int port) throws IOException {
        this(repository, onAttendance, host, port, null);
    }

    void setPublicUrl(String url) throws IOException {
        String validated = validatePublicUrl(url);
        if (validated == null) throw new IOException("Missing public tunnel address.");
        baseUrl = validated;
    }

    public static String configuredPublicUrl() throws IOException {
        String value = System.getenv("ATTENDANCE_PUBLIC_URL");
        if (value == null) {
            java.nio.file.Path config = java.nio.file.Path.of("config", "mobile.properties");
            if (java.nio.file.Files.exists(config)) {
                Properties settings = new Properties();
                try (var input = java.nio.file.Files.newInputStream(config)) { settings.load(input); }
                value = settings.getProperty("public.url");
            }
        }
        return validatePublicUrl(value);
    }

    private static String validatePublicUrl(String value) throws IOException {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.strip());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException();
            return "https://" + uri.getRawAuthority();
        } catch (IllegalArgumentException e) { throw new IOException("Public attendance URL must be an HTTPS address without a path."); }
    }

    public MobileAttendanceServer(AttendanceRepository repository, Consumer<String> onAttendance,
                                  String host, int port, String publicUrl) throws IOException {
        this.repository = repository;
        this.onAttendance = onAttendance;
        String validatedUrl = validatePublicUrl(publicUrl);
        new SecureRandom().nextBytes(secret);
        server = HttpServer.create(new InetSocketAddress(port), 16);
        baseUrl = validatedUrl == null ? "http://" + host + ":" + server.getAddress().getPort() : validatedUrl;
        server.createContext("/", this::handle);
        server.setExecutor(workers);
        server.start();
    }

    public String baseUrl() { return baseUrl; }

    public static String localAddress() throws IOException {
        String configured = System.getenv("ATTENDANCE_HOST");
        if (configured != null && !configured.isBlank()) {
            InetAddress address = InetAddress.getByName(configured);
            if (!(address instanceof Inet4Address)) throw new IOException("ATTENDANCE_HOST must be an IPv4 address.");
            return address.getHostAddress();
        }
        // Ask the OS for the default-route interface; UDP connect sends no packets.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("192.0.2.1"), 80);
            InetAddress address = socket.getLocalAddress();
            if (address instanceof Inet4Address && !address.isAnyLocalAddress() && !address.isLoopbackAddress())
                return address.getHostAddress();
        } catch (SocketException ignored) { }
        for (NetworkInterface adapter : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!adapter.isUp() || adapter.isLoopback() || adapter.isVirtual()) continue;
            for (InetAddress address : Collections.list(adapter.getInetAddresses()))
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
        }
        throw new IOException("Connect this computer to Wi-Fi and restart for mobile scanning.");
    }

    String urlFor(String roll, long now) {
        String data = "student." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(roll.getBytes(StandardCharsets.UTF_8));
        return baseUrl + "/a?c=" + data + "." + sign(data);
    }

    synchronized String classUrl(long now) {
        String data = "class." + classToken;
        return baseUrl + "/check-in?c=" + data + "." + sign(data);
    }

    synchronized void regenerateClassQr() {
        classToken = UUID.randomUUID().toString();
        classQrUsed = false;
    }

    synchronized boolean isClassQrUsed() { return classQrUsed; }

    private synchronized void verifyClass(String token, long now) throws IOException {
        String expected = classUrl(now).substring((baseUrl + "/check-in?c=").length());
        if (token.length() > 128 || !MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII)))
            throw new IOException("This QR was replaced or is invalid. Scan the current class QR and try again.");
        if (classQrUsed)
            throw new IOException("This QR has already been used. Ask the teacher to generate a new QR for the next student.");
    }

    private synchronized boolean submitClass(String token, String roll, String name) throws IOException, SQLException {
        // Verification, database commit and consumption share the same lock as manual replacement.
        // A failed validation or database write leaves the QR available for retry.
        verifyClass(token, System.currentTimeMillis());
        boolean added = repository.selfCheckIn(roll, name);
        classQrUsed = true;
        return added;
    }

    private void checkIn(HttpExchange exchange, String method) throws IOException, SQLException {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || !query.startsWith("c=")) throw new IOException("Scan the class QR to open the attendance form.");
        String token = query.substring(2);
        verifyClass(token, System.currentTimeMillis());
        if (method.equals("GET")) {
            respond(exchange, 200, "Student attendance", "<p>Enter your own details to mark attendance.</p>"
                    + "<p>One QR accepts one student. There is no time limit to submit your details.</p>"
                    + "<form method=\"post\" action=\"/check-in?c=" + escape(token) + "\">"
                    + "<label for=\"roll\">Roll number</label><input id=\"roll\" name=\"roll\" maxlength=\"30\" required autocomplete=\"off\">"
                    + "<label for=\"name\">Full name</label><input id=\"name\" name=\"name\" maxlength=\"100\" required autocomplete=\"name\">"
                    + "<button type=\"submit\">Mark me present</button></form>");
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded"))
            throw new IOException("Submit your details using the attendance form.");
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) throw new IOException("The submitted form is too large.");
        Map<String, String> fields = new HashMap<>();
        try {
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                if (fields.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) { throw new IOException("Invalid form. Scan the QR and try again."); }
        String roll = fields.getOrDefault("roll", "").strip().toUpperCase(Locale.ROOT);
        String name = fields.getOrDefault("name", "").strip();
        boolean added = submitClass(token, roll, name);
        onAttendance.accept((added ? "Marked PRESENT: " : "Already PRESENT today: ") + roll);
        respond(exchange, 200, added ? "Attendance saved" : "Already present today",
                "<p>" + escape(name) + " &middot; " + escape(roll) + "</p><p>This QR is now used. Ask the teacher to generate a new QR for the next student.</p>");
    }

    String decodeUrl(String url, long now) throws IOException {
        if (!url.startsWith(baseUrl + "/a?c=")) throw new IOException("Scan a current QR from this running application.");
        return verify(url.substring((baseUrl + "/a?c=").length()), now);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    Arrays.copyOf(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)), 16));
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    private String verify(String token, long now) throws IOException {
        try {
            if (token.length() > 512) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            if (!parts[0].equals("student")) throw new IllegalArgumentException();
            String roll = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            if (roll.isBlank() || roll.length() > 30 || roll.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException();
            return roll;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid QR link. Scan a new QR from the running application.");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("POST")) {
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                respond(exchange, 405, "Unsupported request", "Open the QR link with your camera.");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/check-in")) { checkIn(exchange, method); return; }
            if (path.equals("/") && method.equals("GET")) {
                respond(exchange, 200, "Mobile attendance is ready", "Scan the current class QR on the computer to enter your details and mark attendance.");
                return;
            }
            if (!path.equals("/a")) { respond(exchange, 404, "Page not found", "Scan your student QR again."); return; }
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null || !query.startsWith("c=")) throw new IOException("Missing QR link. Scan your student QR again.");
            String token = query.substring(2);
            String roll = verify(token, System.currentTimeMillis());
            String name = repository.findStudentName(roll);
            if (name == null) { respond(exchange, 404, "Student not found", "Register the student in the desktop app first."); return; }
            if (method.equals("GET")) {
                respond(exchange, 200, "Confirm attendance", "<p>" + escape(name) + " · " + escape(roll)
                        + "</p><p>There is no time limit while the app is running.</p><form method=\"post\" action=\"/a?c="
                        + escape(token) + "\"><button type=\"submit\">Mark me present</button></form>");
            } else {
                verify(token, System.currentTimeMillis());
                boolean added = repository.markPresentFromQr(roll);
                String result = (added ? "Marked PRESENT: " : "Already PRESENT today: ") + roll;
                onAttendance.accept(result);
                respond(exchange, 200, added ? "Attendance saved" : "Already present today",
                        "<p>" + escape(name) + " · " + escape(roll) + "</p><p>You can close this page.</p>");
            }
        } catch (SQLException e) {
            if ("45000".equals(e.getSQLState())) respond(exchange, 400, "Check your details", escape(e.getMessage()));
            else respond(exchange, 503, "Could not save attendance", "Check MySQL on the computer, then scan a fresh QR and retry.");
        } catch (IOException e) {
            respond(exchange, 400, "Please scan again", escape(e.getMessage()));
        } finally { exchange.close(); }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void respond(HttpExchange exchange, int status, String title, String body) throws IOException {
        byte[] bytes = ("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>" + title + "</title>"
                + "<style>body{margin:0;background:#eef4fa;color:#1c375c;font:18px system-ui;padding:24px}"
                + "main{max-width:440px;margin:8vh auto;background:white;padding:28px;border-radius:18px}"
                + "h1{font-size:28px}button{width:100%;padding:16px;border:0;border-radius:10px;background:#1c375c;color:white;font:inherit}"
                + "label{display:block;margin-top:20px}input{box-sizing:border-box;width:100%;padding:14px;margin:8px 0 20px;border:1px solid #9aafc6;border-radius:8px;font:inherit}"
                + "p{line-height:1.5}</style><main><h1>" + title + "</h1>" + body + "</main></html>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() { server.stop(0); workers.shutdownNow(); }
}
