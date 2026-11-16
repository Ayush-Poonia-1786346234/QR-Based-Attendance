import com.github.sarxos.webcam.Webcam;

/** Opens the default camera briefly; no image is saved or transmitted. */
public final class WebcamSmokeTest {
    public static void main(String[] args) throws Exception {
        Webcam camera = Webcam.getDefault(10000);
        if (camera == null) { System.out.println("No webcam detected; image scanning remains available."); return; }
        try {
            if (!camera.open()) throw new IllegalStateException("Camera did not open");
            if (camera.getImage() == null) throw new IllegalStateException("No camera frame");
            System.out.println("PASS: Webcam opened and returned a frame.");
        } finally { camera.close(); }
    }
}
