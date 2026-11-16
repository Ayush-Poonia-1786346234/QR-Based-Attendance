public final class PublicQrTest {
    public static void main(String[] args) throws Exception {
        try (MobileAttendanceServer server = new MobileAttendanceServer(new AttendanceRepository(), message -> {}, "127.0.0.1", 0, "https://attendance.example.com/")) {
            QrCodeService.setMobileServer(server);
            if (!server.classUrl(0).startsWith("https://attendance.example.com/check-in?c=")) throw new AssertionError("Class QR has local URL");
            if (!"TEST01".equals(QrCodeService.decode(QrCodeService.generate("TEST01")))) throw new AssertionError("HTTPS QR decode failed");
            String previous = server.classUrl(0);
            server.setPublicUrl("https://new-tunnel.example.com");
            if (!server.classUrl(0).startsWith("https://new-tunnel.example.com/check-in?c="))
                throw new AssertionError("QR did not use the connected tunnel address");
            if (!server.classUrl(0).split("\\?c=")[1].equals(previous.split("\\?c=")[1]))
                throw new AssertionError("Changing origin changed the signed class token");
            previous = server.classUrl(0);
            server.regenerateClassQr();
            if (previous.equals(server.classUrl(0))) throw new AssertionError("Public QR did not regenerate");
        } finally { QrCodeService.setMobileServer(null); }
        try {
            new MobileAttendanceServer(new AttendanceRepository(), message -> {}, "127.0.0.1", 0, "https://example.com/incorrect-path");
            throw new AssertionError("Invalid public origin accepted");
        } catch (java.io.IOException expected) { }
        System.out.println("PASS: public HTTPS QR generation, decoding, regeneration and configuration validation.");
    }
}
