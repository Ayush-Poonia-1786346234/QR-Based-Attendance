import com.github.sarxos.webcam.Webcam;
import com.google.zxing.ReaderException;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Image decoding and one-shot live camera scanning. Student QR codes appear in the main table. */
public final class QrDialog extends JDialog {
    private final AttendanceRepository repository;
    private final Consumer<String> onAttendance;
    private final JLabel preview = new JLabel("Choose an image or scan a student's QR with your webcam.", SwingConstants.CENTER);
    private final JLabel message = new JLabel("Student QR codes appear beside each record in the main window.");
    private final JButton image = new JButton("Scan Image");
    private final JButton camera = new JButton("Scan Webcam");
    private final JButton stop = new JButton("Stop Camera");
    private volatile boolean stopCamera;

    public QrDialog(Frame owner, AttendanceRepository repository, Consumer<String> onAttendance) {
        super(owner, "Scan Attendance", true);
        this.repository = repository;
        this.onAttendance = onAttendance;
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel();
        buttons.add(image); buttons.add(camera); buttons.add(stop);
        bottom.add(buttons, BorderLayout.NORTH); bottom.add(message, BorderLayout.SOUTH);
        add(preview, BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);
        preview.setPreferredSize(new Dimension(640, 400));
        stop.setEnabled(false);
        image.addActionListener(e -> scanImage());
        camera.addActionListener(e -> scanCamera());
        stop.addActionListener(e -> { stopCamera = true; message.setText("Stopping camera..."); });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { stopCamera = true; }
        });
        pack(); setLocationRelativeTo(owner);
    }

    private void busy(boolean value) {
        image.setEnabled(!value); camera.setEnabled(!value);
    }

    private <T> void work(Callable<T> task, Consumer<T> success) {
        busy(true);
        new SwingWorker<T, Void>() {
            protected T doInBackground() throws Exception { return task.call(); }
            protected void done() {
                if (!isDisplayable()) return;
                busy(false);
                try { success.accept(get()); }
                catch (Exception e) { showFailure(e.getCause() == null ? e : e.getCause()); }
            }
        }.execute();
    }

    private void showFailure(Throwable e) {
        if (e instanceof ReaderException) message.setText("No readable QR found. Try a clearer image with the whole code visible.");
        else if (e instanceof SQLException && !"45000".equals(((SQLException)e).getSQLState()))
            message.setText("Database unavailable. Check MySQL and your configuration, then retry.");
        else message.setText(e.getMessage() == null ? "Operation failed. Please retry." : e.getMessage());
    }

    private void scanImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("QR images (PNG, JPEG, BMP)", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        message.setText("Reading QR image...");
        work(() -> {
            if (file.length() > 20L * 1024 * 1024) throw new IOException("Choose a QR image smaller than 20 MB.");
            return QrCodeService.decode(ImageIO.read(file));
        }, this::markPresent);
    }

    private void markPresent(String roll) {
        message.setText("QR read. Saving attendance...");
        work(() -> repository.markPresentFromQr(roll), added -> {
            String result = added ? "Marked PRESENT: " + roll : "Already PRESENT today: " + roll;
            message.setText(result);
            onAttendance.accept(result);
        });
    }

    private void scanCamera() {
        busy(true); stop.setEnabled(true); stopCamera = false;
        preview.setIcon(null); preview.setText("Opening default webcam...");
        message.setText("Hold a student QR code in front of the camera.");
        new SwingWorker<String, BufferedImage>() {
            protected String doInBackground() throws Exception {
                Webcam webcam = null;
                try {
                    webcam = Webcam.getDefault(10000);
                    if (webcam == null) throw new IOException("No webcam found. Connect a camera or use Scan Image.");
                    if (stopCamera) return null;
                    Dimension best = java.util.Arrays.stream(webcam.getViewSizes())
                            .max(java.util.Comparator.comparingLong(d -> (long) d.width * d.height)).orElse(null);
                    if (best != null) webcam.setViewSize(best);
                    if (!webcam.open()) throw new IOException("Cannot open webcam. Close other camera apps and retry.");
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
                    while (!stopCamera && System.nanoTime() < deadline) {
                        BufferedImage frame = webcam.getImage();
                        if (frame != null) {
                            publish(frame);
                            try { return QrCodeService.decode(frame); }
                            catch (ReaderException ignored) { /* Keep looking for a QR. */ }
                        }
                        Thread.sleep(100);
                    }
                    return null;
                } finally {
                    if (webcam != null && webcam.isOpen()) webcam.close();
                }
            }
            protected void process(List<BufferedImage> frames) {
                if (!isDisplayable() || stopCamera) return;
                BufferedImage frame = frames.get(frames.size() - 1);
                double scale = Math.min(1.0, Math.min(620.0 / frame.getWidth(), 380.0 / frame.getHeight()));
                Image displayed = scale < 1.0 ? frame.getScaledInstance((int)(frame.getWidth() * scale),
                        (int)(frame.getHeight() * scale), Image.SCALE_FAST) : frame;
                preview.setText(""); preview.setIcon(new ImageIcon(displayed));
            }
            protected void done() {
                stop.setEnabled(false);
                if (!isDisplayable()) return;
                busy(false);
                try {
                    String roll = get();
                    if (roll != null && !stopCamera) markPresent(roll);
                    else message.setText("Camera stopped. No attendance was saved.");
                } catch (Exception e) {
                    message.setText("Camera scan failed. Check camera access, or use Scan Image.");
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) showFailure(cause);
                }
            }
        }.execute();
    }
}
