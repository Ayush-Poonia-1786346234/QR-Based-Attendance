CREATE DATABASE IF NOT EXISTS qr_attendance;
USE qr_attendance;

CREATE TABLE students (
    roll_number VARCHAR(30) PRIMARY KEY,
    student_name VARCHAR(100) NOT NULL
);

CREATE TABLE attendance (
    attendance_id INT AUTO_INCREMENT PRIMARY KEY,
    roll_number VARCHAR(30) NOT NULL,
    attendance_date DATE NOT NULL,
    attendance_time TIME NOT NULL,
    attendance_status VARCHAR(20) DEFAULT 'PRESENT',
    FOREIGN KEY (roll_number) REFERENCES students(roll_number)
);

INSERT INTO students (roll_number, student_name) VALUES
('101', 'Ayush Poonia'),
('045', 'Devanshu Saini'),
('234', 'Tyler');

INSERT INTO attendance
    (roll_number, attendance_date, attendance_time, attendance_status)
VALUES
    ('101', CURRENT_DATE, CURRENT_TIME, 'PRESENT'),
    ('045', CURRENT_DATE, CURRENT_TIME, 'PRESENT'),
    ('234', CURRENT_DATE, CURRENT_TIME, 'PRESENT');
