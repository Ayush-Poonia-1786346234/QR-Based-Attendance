import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import java.util.List;
import javax.swing.*;

public class Main extends JFrame implements ActionListener {
    private final JTextField rollNumberField = new JTextField(20);
    private final JTextField studentNameField = new JTextField(20);
    private final JTextField editNumberField = new JTextField(20);
    private final StudentRecordTable recordTable = new StudentRecordTable();
    private final JLabel statusLabel = new JLabel("Ready");

    private final JButton registerButton = new JButton("Register Student");
    private final JButton presentButton = new JButton("Mark Present");
    private final JButton editButton = new JButton("Update Record");
    private final JButton scanItem = new JButton("Scan attendance...");
    private final JButton clearButton = new JButton("Clear");
    private final JButton exitButton = new JButton("Exit");

    private final AttendanceRepository repository = new AttendanceRepository();
    private MobileAttendanceServer mobileServer;
    private AttendanceTunnel attendanceTunnel;
    private SwingWorker<Void, Void> mobileStartup;
    private boolean closing;
    private final CardLayout screens = new CardLayout();
    private final JPanel screenPanel = new JPanel(screens);
    private final javax.swing.JLabel classQr = new javax.swing.JLabel("Starting class QR...", javax.swing.SwingConstants.CENTER);
    private final javax.swing.JButton generateQr = new javax.swing.JButton("Generate new QR");
    private List<AttendanceRepository.AttendanceRecord> records = List.of();

    public Main() {
        setTitle("QR Attendance");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(900, 700);
        setMinimumSize(new Dimension(820, 660));
        setLocationRelativeTo(null);
        JPanel root = AttendanceUi.panel(new BorderLayout(), AttendanceUi.PAPER);
        setContentPane(root);

        JPanel header = AttendanceUi.panel(new BorderLayout(), Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AttendanceUi.LINE),
                BorderFactory.createEmptyBorder(18, 28, 18, 28)));
        JLabel brand = AttendanceUi.label("Attendance", 21, AttendanceUi.INK);
        brand.setFont(AttendanceUi.font(21, true)); header.add(brand, BorderLayout.WEST);
        JPanel navigation = AttendanceUi.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0), Color.WHITE);
        JButton showQr = new JButton("Class QR");
        JButton showRecords = new JButton("Student records");
        AttendanceUi.button(showQr, true); AttendanceUi.button(showRecords, false);
        showQr.addActionListener(e -> { screens.show(screenPanel, "qr"); AttendanceUi.button(showQr, true); AttendanceUi.button(showRecords, false); });
        showRecords.addActionListener(e -> { screens.show(screenPanel, "records"); AttendanceUi.button(showQr, false); AttendanceUi.button(showRecords, true); });
        navigation.add(showQr); navigation.add(showRecords); header.add(navigation, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel qrPanel = AttendanceUi.panel(new GridBagLayout(), AttendanceUi.PAPER);
        JPanel content = AttendanceUi.panel(new BorderLayout(32, 0), AttendanceUi.PAPER);
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel intro = AttendanceUi.stack();
        intro.setPreferredSize(new Dimension(300, 420));
        AttendanceUi.line(intro, AttendanceUi.label("CLASS CHECK-IN", 11, AttendanceUi.GREEN), 18);
        JLabel title = AttendanceUi.label("<html>Ready for<br>check-in.</html>", 34, AttendanceUi.INK);
        title.setFont(AttendanceUi.font(34, true)); AttendanceUi.line(intro, title, 18);
        AttendanceUi.line(intro, AttendanceUi.label("<html>Mark your attendance in a few seconds.<br>No app or sign-in needed.</html>", 14, AttendanceUi.MUTED), 32);
        AttendanceUi.line(intro, AttendanceUi.label("01   Scan the QR with your phone", 13, AttendanceUi.INK), 16);
        AttendanceUi.line(intro, AttendanceUi.label("02   Enter your roll number and name", 13, AttendanceUi.INK), 16);
        AttendanceUi.line(intro, AttendanceUi.label("03   Tap Mark me present", 13, AttendanceUi.INK), 32);
        content.add(intro, BorderLayout.WEST);

        JPanel qrCard = AttendanceUi.panel(new BorderLayout(0, 12), Color.WHITE);
        qrCard.setPreferredSize(new Dimension(360, 460));
        qrCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AttendanceUi.LINE),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)));
        JLabel qrCaption = AttendanceUi.label("SCAN TO CHECK IN", 11, AttendanceUi.MUTED);
        qrCaption.setHorizontalAlignment(SwingConstants.CENTER); qrCard.add(qrCaption, BorderLayout.NORTH);
        classQr.setFont(AttendanceUi.font(16, true)); classQr.setForeground(AttendanceUi.INK);
        classQr.setHorizontalTextPosition(SwingConstants.CENTER); classQr.setVerticalTextPosition(SwingConstants.BOTTOM);
        classQr.setIconTextGap(18); classQr.setText("Preparing your QR...");
        qrCard.add(classQr, BorderLayout.CENTER);
        JPanel qrActions = AttendanceUi.panel(new BorderLayout(0, 10), Color.WHITE);
        AttendanceUi.button(generateQr, true); generateQr.setEnabled(false);
        generateQr.addActionListener(e -> {
            if (mobileServer == null) return;
            mobileServer.regenerateClassQr(); refreshClassQr();
            statusLabel.setText("New QR ready for the next student.");
        });
        JLabel singleUse = AttendanceUi.label("One QR. One student. One check-in.", 11, AttendanceUi.MUTED);
        singleUse.setHorizontalAlignment(SwingConstants.CENTER);
        qrActions.add(generateQr, BorderLayout.CENTER); qrActions.add(singleUse, BorderLayout.SOUTH);
        qrCard.add(qrActions, BorderLayout.SOUTH); content.add(qrCard, BorderLayout.CENTER);
        qrPanel.add(content);

        JPanel recordsPanel = AttendanceUi.panel(new BorderLayout(0, 18), AttendanceUi.PAPER);
        recordsPanel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel fields = AttendanceUi.panel(new GridLayout(2, 3, 16, 8), AttendanceUi.PAPER);
        fields.add(AttendanceUi.label("Roll number", 12, AttendanceUi.MUTED));
        fields.add(AttendanceUi.label("Full name", 12, AttendanceUi.MUTED));
        fields.add(AttendanceUi.label("Record no. (for corrections)", 12, AttendanceUi.MUTED));
        for (JTextField field : new JTextField[]{rollNumberField, studentNameField, editNumberField}) {
            field.setFont(AttendanceUi.font(14, false));
            field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AttendanceUi.LINE), BorderFactory.createEmptyBorder(9, 10, 9, 10)));
            fields.add(field);
        }
        recordsPanel.add(fields, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(recordTable);
        tableScroll.setColumnHeaderView(recordTable.getTableHeader());
        tableScroll.setBorder(BorderFactory.createLineBorder(AttendanceUi.LINE)); recordsPanel.add(tableScroll, BorderLayout.CENTER);
        JPanel actions = AttendanceUi.panel(new FlowLayout(FlowLayout.LEFT, 8, 0), AttendanceUi.PAPER);
        for (JButton button : new JButton[]{presentButton, registerButton, editButton, clearButton, scanItem}) {
            AttendanceUi.button(button, button == presentButton); actions.add(button);
        }
        recordsPanel.add(actions, BorderLayout.SOUTH);
        screenPanel.add(qrPanel, "qr"); screenPanel.add(recordsPanel, "records");
        root.add(screenPanel, BorderLayout.CENTER);

        JPanel footer = AttendanceUi.panel(new BorderLayout(16, 0), Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AttendanceUi.LINE),
                BorderFactory.createEmptyBorder(14, 28, 14, 28)));
        JLabel mobileStatus = AttendanceUi.label("Connecting...", 12, AttendanceUi.MUTED);
        statusLabel.setFont(AttendanceUi.font(12, false)); statusLabel.setForeground(AttendanceUi.MUTED);
        footer.add(mobileStatus, BorderLayout.WEST); footer.add(statusLabel, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        mobileStartup = new SwingWorker<>() {
            private volatile MobileAttendanceServer startingServer;
            private volatile AttendanceTunnel startingTunnel;
            private String publicUrl;

            @Override protected Void doInBackground() throws Exception {
                try {
                    publicUrl = MobileAttendanceServer.configuredPublicUrl();
                    startingServer = new MobileAttendanceServer(repository,
                    message -> EventQueue.invokeLater(() -> {
                        if (isDisplayable()) {
                            refreshClassQr();
                            runDatabaseTask(() -> { }, message, false);
                        }
                    }), "127.0.0.1", 8765, publicUrl);
                    if (publicUrl == null) {
                        startingTunnel = new AttendanceTunnel(8765);
                        publicUrl = startingTunnel.awaitUrl();
                        startingServer.setPublicUrl(publicUrl);
                    }
                    return null;
                } catch (Exception e) {
                    if (startingServer != null) startingServer.close();
                    if (startingTunnel != null) startingTunnel.close();
                    throw e;
                } finally {
                    if (isCancelled()) {
                        if (startingServer != null) startingServer.close();
                        if (startingTunnel != null) startingTunnel.close();
                    }
                }
            }

            @Override protected void done() {
                if (closing) {
                    if (startingServer != null) startingServer.close();
                    if (startingTunnel != null) startingTunnel.close();
                    return;
                }
                try {
                    get();
                    mobileServer = startingServer;
                    attendanceTunnel = startingTunnel;
                    QrCodeService.setMobileServer(mobileServer);
                    displayRecords();
                    mobileStatus.setText("Online"); mobileStatus.setForeground(AttendanceUi.GREEN); mobileStatus.setToolTipText(mobileServer.baseUrl());
                    refreshClassQr();
                    generateQr.setEnabled(true);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    mobileStatus.setText("Connection unavailable"); mobileStatus.setToolTipText(cause.getMessage());
                    classQr.setText("<html><div style='text-align:center'>Unable to connect<br><br><span style='font-size:11px;font-weight:normal'>Check your internet and restart the app.</span></div></html>");
                }
            }
        };
        mobileStatus.setText("Connecting...");
        mobileStartup.execute();

        registerButton.addActionListener(this);
        presentButton.addActionListener(this);
        editButton.addActionListener(this);
        scanItem.addActionListener(this);
        clearButton.addActionListener(this);
        exitButton.addActionListener(this);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
                dispose();
            }
        });
        runDatabaseTask(() -> { }, "Records loaded from MySQL.", false);
    }

    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == registerButton) {
            addRecord("REGISTERED");
        } else if (event.getSource() == presentButton) {
            addRecord("PRESENT");
        } else if (event.getSource() == editButton) {
            updateRecord();
        } else if (event.getSource() == scanItem) {
            new QrDialog(this, repository,
                    message -> runDatabaseTask(() -> { }, message, false)).setVisible(true);
        } else if (event.getSource() == clearButton) {
            rollNumberField.setText("");
            studentNameField.setText("");
            editNumberField.setText("");
            rollNumberField.requestFocus();
            statusLabel.setText("Form cleared");
        } else if (event.getSource() == exitButton) {
            dispose();
        }
    }

    private void addRecord(String status) {
        String rollNumber = rollNumberField.getText().trim().toUpperCase();
        String studentName = studentNameField.getText().trim();

        if (rollNumber.length() == 0 || studentName.length() == 0) {
            statusLabel.setText("Enter both roll number and student name.");
            return;
        }

        if (rollNumber.length() > 30 || studentName.length() > 100) {
            statusLabel.setText("Roll number: maximum 30 characters; name: maximum 100.");
            return;
        }

        runDatabaseTask(() -> repository.add(rollNumber, studentName, status),
                "Record saved to MySQL successfully.", true);
    }

    private void updateRecord() {
        String numberText = editNumberField.getText().trim();
        String newRollNumber = rollNumberField.getText().trim().toUpperCase();
        String newStudentName = studentNameField.getText().trim();

        if (numberText.length() == 0 || newRollNumber.length() == 0
                || newStudentName.length() == 0) {
            statusLabel.setText("Enter record number, corrected roll number and name.");
            return;
        }

        int recordIndex;
        try {
            recordIndex = Integer.parseInt(numberText) - 1;
        } catch (NumberFormatException exception) {
            statusLabel.setText("Record number must be numeric.");
            return;
        }

        if (recordIndex < 0 || recordIndex >= records.size()) {
            statusLabel.setText("Record number not found.");
            return;
        }

        if (newRollNumber.length() > 30 || newStudentName.length() > 100) {
            statusLabel.setText("Roll number: maximum 30 characters; name: maximum 100.");
            return;
        }
        int id = records.get(recordIndex).id();
        runDatabaseTask(() -> repository.update(id, newRollNumber, newStudentName),
                "Record " + (recordIndex + 1) + " updated successfully.", true);
    }

    private void displayRecords() {
        recordTable.setRecords(records);
    }

    @FunctionalInterface
    private interface DatabaseTask { void execute() throws SQLException; }

    private void runDatabaseTask(DatabaseTask task, String success, boolean clearForm) {
        setDatabaseBusy(true);
        statusLabel.setText("Connecting to MySQL...");
        new SwingWorker<List<AttendanceRepository.AttendanceRecord>, Void>() {
            private boolean saved;
            protected List<AttendanceRepository.AttendanceRecord> doInBackground() throws Exception {
                task.execute();
                saved = clearForm;
                return repository.findAll();
            }
            protected void done() {
                try {
                    records = get();
                    displayRecords();
                    statusLabel.setText(success);
                } catch (Exception exception) {
                    statusLabel.setText(saved ? "Saved, but refresh failed. Restart to reload records."
                            : "Database operation failed. Check MySQL, schema.sql and config/database.properties.");
                    Throwable cause = exception.getCause();
                    System.err.println("Database operation failed (" +
                            (cause instanceof SQLException ? ((SQLException) cause).getSQLState() : "configuration") + ").");
                } finally {
                    if (saved) {
                        rollNumberField.setText("");
                        studentNameField.setText("");
                        editNumberField.setText("");
                    }
                    setDatabaseBusy(false);
                }
            }
        }.execute();
    }

    private void setDatabaseBusy(boolean busy) {
        registerButton.setEnabled(!busy);
        presentButton.setEnabled(!busy);
        editButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        scanItem.setEnabled(!busy);
        rollNumberField.setEnabled(!busy);
        studentNameField.setEnabled(!busy);
        editNumberField.setEnabled(!busy);
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Main().setVisible(true);
            }
        });
    }

    private void refreshClassQr() {
        if (mobileServer == null) return;
        if (mobileServer.isClassQrUsed()) {
            classQr.setIcon(AttendanceUi.checkIcon());
            classQr.setText("<html><div style='text-align:center'>Check-in complete<br><br><span style='font-size:11px;font-weight:normal;color:#69736d'>Create a new QR for the next student.</span></div></html>");
            generateQr.setText("Next student");
            return;
        }
        long now = System.currentTimeMillis();
        try {
                classQr.setIcon(new javax.swing.ImageIcon(QrCodeService.generatePayload(mobileServer.classUrl(now), 300)));
                classQr.setText(""); generateQr.setText("Generate new QR");

            } catch (com.google.zxing.WriterException e) {
                classQr.setIcon(null);
                classQr.setText("Could not generate QR. Restart the application.");
            }
    }

    @Override public void dispose() {
        closing = true;
        if (mobileStartup != null) mobileStartup.cancel(true);
        if (attendanceTunnel != null) attendanceTunnel.close();
        if (mobileServer != null) {
            QrCodeService.setMobileServer(null);
            mobileServer.close();
            mobileServer = null;
        }
        super.dispose();
    }
}



