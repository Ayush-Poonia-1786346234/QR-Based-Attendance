import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.swing.*;

public final class QrRotationTest {
    private static final long NOW = 1_800_000_000_000L;
    public static void main(String[] args) throws Exception {
        for (String roll : List.of("ST001", "123456789012345678901234567890", "学生१२३")) {
            for (int size : new int[]{160, 360}) {
                BufferedImage qr = QrCodeService.generate(roll, size, NOW);
                check(roll.equals(QrCodeService.decode(qr, NOW + 9_999)), "Current QR must decode");
                check(roll.equals(QrCodeService.decode(qr, NOW + 86_400_000)), "QR must not expire with time");
                BufferedImage next = QrCodeService.generate(roll, size, NOW + 10_000);
                check(roll.equals(QrCodeService.decode(next, NOW + 10_000)), "Next QR must decode");
                check(java.util.Arrays.equals(qr.getRGB(0,0,size,size,null,0,size), next.getRGB(0,0,size,size,null,0,size)), "QR must remain stable without manual replacement");
            }
        }
        reject(raw("QRATTENDANCE:1:U1QwMDE="), NOW, "outdated");
        reject(raw("QRATTENDANCE:3:bad:U1QwMDE="), NOW, "invalid");
        reject(raw("https://example.com"), NOW, "not a student");
        SwingUtilities.invokeAndWait(() -> {
            StudentRecordTable table = new StudentRecordTable();
            table.setRecords(List.of(new AttendanceRepository.AttendanceRecord(1, "ST001", "Student", "REGISTERED")));
            check(table.getValueAt(0, 4) instanceof ImageIcon, "Table QR missing");
            try {
                check("ST001".equals(QrCodeService.decode((BufferedImage)((ImageIcon)table.getValueAt(0,4)).getImage())), "Table QR unreadable");
            } catch (Exception e) { throw new AssertionError(e); }
        });
        System.out.println("PASS: QR sizes, Unicode, no automatic expiry, stable generation, legacy/invalid codes and table QR.");
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    private static void reject(BufferedImage qr, long now, String message) throws Exception {
        try { QrCodeService.decode(qr, now); throw new AssertionError("Invalid QR accepted"); }
        catch (IOException expected) { check(expected.getMessage().contains(message), expected.getMessage()); }
    }
    private static BufferedImage raw(String payload) throws Exception {
        BitMatrix bits = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 360, 360);
        BufferedImage image = new BufferedImage(360,360,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<360;y++) for(int x=0;x<360;x++) image.setRGB(x,y,bits.get(x,y)?0:0xffffff);
        return image;
    }
}
