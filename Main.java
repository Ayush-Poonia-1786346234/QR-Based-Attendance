import java.awt.*;
import java.awt.event.*;

public class Main extends Frame implements ActionListener {
    private final TextField rollNumberField = new TextField(20);
    private final TextField studentNameField = new TextField(20);
    private final TextField editNumberField = new TextField(20);
    private final TextArea recordArea = new TextArea();
    private final Label statusLabel = new Label("Ready", Label.CENTER);

    private final Button registerButton = new Button("Register Student");
    private final Button presentButton = new Button("Mark Present");
    private final Button editButton = new Button("Update Record");
    private final Button qrButton = new Button("Scan QR (Demo)");
    private final Button clearButton = new Button("Clear");
    private final Button exitButton = new Button("Exit");

    private final String[] rollNumbers = new String[100];
    private final String[] studentNames = new String[100];
    private final String[] attendanceStatus = new String[100];
    private int recordCount = 0;

    public Main() {
        setTitle("QR-Based Attendance Management System");
        setSize(760, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(238, 244, 250));

        Panel formPanel = new Panel(new GridLayout(4, 2, 10, 10));
        formPanel.setBackground(Color.WHITE);

        Label rollLabel = new Label("Roll Number:");
        rollLabel.setFont(new Font("Arial", Font.BOLD, 14));
        Label nameLabel = new Label("Student Name:");
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        Label editLabel = new Label("Record No. to Edit:");
        editLabel.setFont(new Font("Arial", Font.BOLD, 14));

        formPanel.add(rollLabel);
        formPanel.add(rollNumberField);
        formPanel.add(nameLabel);
        formPanel.add(studentNameField);
        formPanel.add(editLabel);
        formPanel.add(editNumberField);
        formPanel.add(registerButton);
        formPanel.add(presentButton);

        Panel centerPanel = new Panel(new BorderLayout(10, 10));
        centerPanel.add(formPanel, BorderLayout.NORTH);

        recordArea.setEditable(false);
        recordArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        recordArea.setText("NO.   ROLL NUMBER        STUDENT NAME             STATUS\n"
                + "------------------------------------------------------------\n");
        centerPanel.add(recordArea, BorderLayout.CENTER);

        Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        buttonPanel.add(editButton);
        buttonPanel.add(qrButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(exitButton);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        statusLabel.setBackground(new Color(28, 55, 92));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        add(statusLabel, BorderLayout.SOUTH);

        registerButton.addActionListener(this);
        presentButton.addActionListener(this);
        editButton.addActionListener(this);
        qrButton.addActionListener(this);
        clearButton.addActionListener(this);
        exitButton.addActionListener(this);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
                dispose();
            }
        });
    }

    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == registerButton) {
            addRecord("REGISTERED");
        } else if (event.getSource() == presentButton) {
            addRecord("PRESENT");
        } else if (event.getSource() == editButton) {
            updateRecord();
        } else if (event.getSource() == qrButton) {
            statusLabel.setText("QR scanning will be added during integration.");
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

        if (recordCount == rollNumbers.length) {
            statusLabel.setText("Maximum record limit reached.");
            return;
        }

        rollNumbers[recordCount] = rollNumber;
        studentNames[recordCount] = studentName;
        attendanceStatus[recordCount] = status;
        recordCount++;
        displayRecords();
        statusLabel.setText("Record added successfully (demo only)");
        rollNumberField.setText("");
        studentNameField.setText("");
        rollNumberField.requestFocus();
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

        if (recordIndex < 0 || recordIndex >= recordCount) {
            statusLabel.setText("Record number not found.");
            return;
        }

        rollNumbers[recordIndex] = newRollNumber;
        studentNames[recordIndex] = newStudentName;
        displayRecords();
        statusLabel.setText("Record " + (recordIndex + 1) + " updated successfully.");
        rollNumberField.setText("");
        studentNameField.setText("");
        editNumberField.setText("");
    }

    private void displayRecords() {
        recordArea.setText("NO.   ROLL NUMBER        STUDENT NAME             STATUS\n"
                + "------------------------------------------------------------\n");

        for (int index = 0; index < recordCount; index++) {
            recordArea.append(String.format("%-5d %-18s %-24s %s%n",
                    index + 1, rollNumbers[index], studentNames[index],
                    attendanceStatus[index]));
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Main().setVisible(true);
            }
        });
    }
}
