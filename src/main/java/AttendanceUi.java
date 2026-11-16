import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/** Shared, lightweight desktop styling; no external look-and-feel dependency. */
final class AttendanceUi {
    static final Color INK = new Color(31, 43, 39);
    static final Color MUTED = new Color(105, 115, 109);
    static final Color GREEN = new Color(33, 91, 68);
    static final Color PAPER = new Color(246, 247, 243);
    static final Color LINE = new Color(222, 227, 220);
    static Font font(int size, boolean bold) { return new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size); }
    static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text); label.setFont(font(size, false)); label.setForeground(color); return label;
    }
    static JPanel panel(LayoutManager layout, Color color) {
        JPanel panel = new JPanel(layout); panel.setBackground(color); return panel;
    }
    static void button(JButton button, boolean primary) {
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setFont(font(13, true)); button.setForeground(primary ? Color.WHITE : INK);
        button.setBackground(primary ? GREEN : Color.WHITE);
        button.setOpaque(true); button.setFocusPainted(true);
        button.setBorder(new CompoundBorder(new LineBorder(primary ? GREEN : LINE), new EmptyBorder(11, 18, 11, 18)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    static JPanel stack() {
        JPanel panel = panel(null, PAPER); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel;
    }
    static void line(JPanel panel, JComponent item, int gap) {
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, item.getPreferredSize().height));
        item.setAlignmentX(Component.LEFT_ALIGNMENT); panel.add(item); panel.add(Box.createVerticalStrut(gap));
    }
    static Icon checkIcon() {
        return new Icon() {
            public int getIconWidth() { return 68; }
            public int getIconHeight() { return 68; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(231, 241, 233)); g.fillOval(x, y, 68, 68);
                g.setColor(GREEN); g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{x+21,x+30,x+47}, new int[]{y+35,y+44,y+25}, 3); g.dispose();
            }
        };
    }
}
