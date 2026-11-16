import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Owns a temporary public tunnel for one attendance application session. */
final class AttendanceTunnel implements AutoCloseable {
    private final Process process;
    private final Thread shutdownHook;
    private final Path log;

    AttendanceTunnel(int port) throws IOException {
        Files.createDirectories(Path.of("target"));
        log = Files.createTempFile(Path.of("target"), "online-tunnel-", ".log");
        String executable = "cloudflared";
        for (String directory : new String[] {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles")}) {
            if (directory != null) {
                Path candidate = Path.of(directory, "cloudflared", "cloudflared.exe");
                if (Files.isRegularFile(candidate)) { executable = candidate.toString(); break; }
            }
        }
        process = new ProcessBuilder(executable, "tunnel", "--no-autoupdate", "--protocol", "http2",
                "--url", "http://127.0.0.1:" + port)
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        shutdownHook = new Thread(process::destroy, "attendance-tunnel-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    String awaitUrl() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        Pattern address = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new IOException("Tunnel stopped. See " + log);
            String output = Files.readString(log);
            var match = address.matcher(output);
            if (match.find() && output.contains("Registered tunnel connection")) return match.group();
            Thread.sleep(250);
        }
        throw new IOException("Tunnel connection timed out. See " + log);
    }

    @Override public void close() {
        process.destroy();
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { /* JVM shutdown is already in progress. */ }
    }
}
