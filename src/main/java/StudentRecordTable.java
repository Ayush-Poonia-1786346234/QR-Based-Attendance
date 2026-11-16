import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Displays a compact, automatically generated QR beside every saved record. */
public final class StudentRecordTable extends JTable {
    private List<AttendanceRepository.AttendanceRecord> records = List.of();
    private final Map<String, ImageIcon> codes = new HashMap<>();
    private final AbstractTableModel model = new AbstractTableModel() {
        private final String[] columns = {"No.", "Roll Number", "Student Name", "Status", "QR"};
        public int getRowCount() { return records.size(); }
        public int getColumnCount() { return columns.length; }
        public String getColumnName(int column) { return columns[column]; }
        public Class<?> getColumnClass(int column) { return column == 4 ? Icon.class : String.class; }
        public Object getValueAt(int row, int column) {
            var record = records.get(row);
            return switch (column) {
                case 0 -> Integer.toString(row + 1);
                case 1 -> record.rollNumber();
                case 2 -> record.studentName();
                case 3 -> record.status();
                default -> codes.get(record.rollNumber());
            };
        }
    };

    public StudentRecordTable() {
        setModel(model);
        setRowHeight(104);
        setFont(AttendanceUi.font(13, false));
        getTableHeader().setFont(AttendanceUi.font(12, true));
        getTableHeader().setReorderingAllowed(false);
        setFillsViewportHeight(true);
        setGridColor(AttendanceUi.LINE); setShowVerticalLines(false); getTableHeader().setBackground(AttendanceUi.PAPER); getTableHeader().setPreferredSize(new Dimension(0, 36));
        setSelectionBackground(new Color(231, 241, 233)); setSelectionForeground(AttendanceUi.INK);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        getColumnModel().getColumn(0).setMaxWidth(48);
        getColumnModel().getColumn(1).setPreferredWidth(140);
        getColumnModel().getColumn(2).setPreferredWidth(260);
        getColumnModel().getColumn(3).setPreferredWidth(120);
        getColumnModel().getColumn(4).setMinWidth(104);
        getColumnModel().getColumn(4).setMaxWidth(104);
        DefaultTableCellRenderer text = new DefaultTableCellRenderer();
        text.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        setDefaultRenderer(String.class, text);
        setToolTipText("Click a student's QR to enlarge or save it.");
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row >= 0 && columnAtPoint(e.getPoint()) == 4) showCode(records.get(row));
            }
        });
    }

    public void setRecords(List<AttendanceRepository.AttendanceRecord> updated) {
        records = List.copyOf(updated);
        refreshCodes();
        model.fireTableDataChanged();
    }

    private void refreshCodes() {
        long now = System.currentTimeMillis();
        Map<String, ImageIcon> next = new HashMap<>();
        for (var record : records) {
            if (next.containsKey(record.rollNumber())) continue;
            try { next.put(record.rollNumber(), new ImageIcon(QrCodeService.generate(record.rollNumber(), 96, now))); }
            catch (com.google.zxing.WriterException e) { throw new IllegalStateException("Unable to generate student QR", e); }
        }
        codes.clear();
        codes.putAll(next);
        if (!records.isEmpty()) model.fireTableRowsUpdated(0, records.size() - 1);
    }

    private void showCode(AttendanceRepository.AttendanceRecord record) {
        try {
            JLabel code = new JLabel();
            JLabel instructions = new JLabel("", SwingConstants.CENTER);
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(code, BorderLayout.CENTER);
            panel.add(instructions, BorderLayout.SOUTH);
            code.setIcon(new ImageIcon(QrCodeService.generate(record.rollNumber())));
            instructions.setText("No time limit - keep the desktop app running");
            int choice = JOptionPane.showOptionDialog(this, panel,
                    record.rollNumber() + " - " + record.studentName(), JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, new String[]{"Save PNG", "Close"}, "Close");
            if (choice != 0) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("student-qr.png"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) file = new File(file.getPath() + ".png");
            if (file.exists() && JOptionPane.showConfirmDialog(this, "Replace " + file.getName() + "?",
                    "Replace image", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            ImageIO.write(QrCodeService.generate(record.rollNumber()), "PNG", file);
            JOptionPane.showMessageDialog(this, "Saved. Keep the desktop app running to use this QR.",
                    "QR saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not display or save the QR code. " + e.getMessage(),
                    "QR code", JOptionPane.ERROR_MESSAGE);
        }
    }
}

