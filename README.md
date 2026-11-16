# QR Attendance Basic

The Swing desktop interface provides a focused class QR view, a completion state with a Next student action, and a Student records view. Record numbering and the Clear action are retained. Records persist in MySQL and are loaded at startup. The QR demo has been replaced with working QR generation, image scanning and webcam scanning.

## Open in IntelliJ IDEA

1. Open this folder as an IntelliJ project. It uses `QRAttendanceBasic.iml`, with no Maven required.
2. Select JDK 17 or newer in **File > Project Structure > Project > SDK**. The module already references `lib/mysql-connector-j-26.10.0.jar` directly.
3. Keep the MySQL80 service running. The database has been initialized on this computer; on another computer, execute `schema.sql` in MySQL Workbench first.
4. Run **QR Attendance**, or open `src/main/java/Main.java` and run its `main` method. Set the working directory to the project root.

Run `build.cmd` to compile or `run.cmd` to compile and launch (you can double-click `run.cmd`). These launch the included PowerShell scripts with a process-only execution-policy override; they do not change your system policy. They use `javac` and `java` from your PATH and the local Connector/J JAR. No dependency download is needed.

The old Maven configuration is preserved as `pom.xml.maven-backup`; it is not used by the project.

## Database configuration

Connection defaults are in `src/main/resources/database.properties`: MySQL on `localhost:3306`, database `qr_attendance`, user `root`. Your supplied password is stored in `config/database.properties`, which is excluded from Git. Keep that file local. `DB_URL`, `DB_USER` and `DB_PASSWORD` environment variables override the files.

The localhost JDBC URL disables TLS and allows public key retrieval for local development. Use a TLS-enabled URL for a remote server.

`schema.sql` creates the database and the original `students` and `attendance` tables if missing. It preserves the original three sample students and adds their sample attendance only when they have no existing attendance. It does not drop tables or overwrite existing names. `schema.original.sql` preserves the supplied SQL exactly.

Register Student adds a REGISTERED entry; Mark Present adds a PRESENT entry, with the database's current date and time. Both create the student if needed. Repeated clicks create separate entries, as in the original app. A roll number identifies one student, so changing that student's name updates the name shown for all their entries. Update Record uses the displayed row number, changes the selected entry's student association and preserves its status/date/time; other attendance entries keep their existing roll numbers. Unreferenced students are retained.

Inputs use prepared statements and writes use transactions. Database failures appear in the status bar; SQL states appear in the console. Clear only clears the form.

## Student self check-in (default startup screen)

Running `run.cmd` now opens a single large **class QR**, with a **Generate new QR** button. There is no timer or automatic expiry. The teacher does not need to enter student details or register students beforehand.

1. Open the app and wait for its online QR. The tunnel starts automatically. Keep the computer connected to the internet; phones can use mobile data or any Wi-Fi.
2. Students scan the class QR using their phone camera and open the link.
3. Each student enters their **roll number** and **full name**, then taps **Mark me present**. There is no time limit to fill the form. Each QR accepts exactly one successful student submission. After submission, the QR is marked used and cannot open another form or accept another submission.
4. A first-time student is registered automatically and marked PRESENT in one database transaction. Repeat submissions create only one PRESENT entry per roll number per database date, including simultaneous submissions.
5. Click **Generate new QR** whenever you want a replacement for the next student. The old class link stops working, including forms already open on it. The new QR stays usable until one student submits, it is replaced, or the app restarts. The desktop hides the used QR and prompts the teacher to generate the next one. Invalid details or failed database writes do not consume the QR; an accepted already-present submission does consume it. Simultaneous submissions from different students cannot both use the same QR.
6. Use **Student records** to view or correct records, and **Class QR** to return to the QR screen.

Roll numbers are trimmed and converted to uppercase; names are trimmed. Existing student names are not overwritten by self check-in. A different name for an existing roll number is rejected so the student can check their details or ask the teacher. These are self-reported details, not a password-based login. The teacher records view is a local menu, not an authenticated admin portal.

## Student QR codes

1. Open **Student records** and register the student if you want an individual QR. A compact QR automatically appears beside their record in the **QR** column, including when existing records load.
2. Click the QR thumbnail to enlarge it, then choose **Save PNG** if you want to save it for immediate scanning. No separate generation step is needed.
3. For mobile scanning, keep the desktop app open and connected to the internet. Phones can use mobile data or any Wi-Fi. Scan the enlarged QR with the phone camera, open the link, then tap **Mark me present** when ready.
4. For desktop scanning, open **Student records > Scan attendance...**, then use **Scan Image** or **Scan Webcam**.
5. Successful attendance submissions mark the registered student PRESENT in MySQL and refresh the main window. Repeat submissions on the same database date do not add another PRESENT entry.

QR codes have no automatic refresh or time limit. The class QR is replaced using **Generate new QR**; individual student codes in the teacher records remain stable while the app runs. The QR contains a signed local attendance URL when the mobile server is running. Opening the link displays a confirmation page; attendance is saved only after tapping **Mark me present**. Links also become invalid when the desktop app restarts.

The webcam stops after reading a code, after two minutes, when you click **Stop Camera**, or when you close the QR window. Frames are not saved or transmitted. If the camera cannot open, check Windows camera permissions and close other apps using it; image scanning remains available.

QR codes contain an application identifier and roll number, not database credentials. Only codes generated by this app are accepted, and the student must still exist in MySQL. These are attendance links, not proof of who is holding the phone; use them with supervised attendance. If a roll number changes, generate a new QR code for it.

### Online attendance (mobile data or any Wi-Fi)

Launch normally with **run.cmd** or IntelliJ's **QR Attendance / Main** run action. The app automatically starts the installed `cloudflared` over HTTP/2, waits for a registered connection, and generates the QR using the new public HTTPS address. The window stays responsive while connecting (up to 90 seconds). `run-online.cmd` remains an alias for normal startup. No separate tunnel window or command is needed.

Keep the app, internet connection, and MySQL running. Closing the app stops its tunnel. Scan the newly displayed QR after each restart; temporary tunnel addresses and attendance links change. The one-QR/one-student rule still applies. Run only one app instance at a time (port 8765).

If startup fails, the window displays the error. Check the internet connection and installed `cloudflared`, then restart the app. Tunnel logs are in `target/online-tunnel-*.log`. Error 1033 on a phone means its tunnel is disconnected; restart the app and scan its new QR. Do not save temporary `trycloudflare.com` addresses in configuration.

Quick Tunnels are intended for testing. For a stable tunnel you manage separately, set `ATTENDANCE_PUBLIC_URL` or `public.url` in `config/mobile.properties`; the app uses that address without starting another tunnel. The environment variable takes precedence. With neither set, automatic online startup is the default. QR links should only be shared with intended students because the form accepts self-reported names and roll numbers.

The local JARs in `lib` include [ZXing](https://github.com/zxing/zxing) for QR encoding/decoding and [Webcam Capture](https://github.com/sarxos/webcam-capture) with BridJ and SLF4J for camera access. IntelliJ and the scripts load these directly without Maven.

## Project structure

```text
QRAttendanceBasic/
  QRAttendanceBasic.iml                IntelliJ Java module
  .idea/modules.xml                   IntelliJ project configuration
  .idea/misc.xml
  lib/mysql-connector-j-26.10.0.jar     Local JDBC driver
  lib/zxing-core-3.5.4.jar             QR encoding/decoding
  lib/webcam-capture-0.3.12.jar        Webcam access
  lib/bridj-0.7.0.jar                 Native camera support
  lib/slf4j-*.jar                     Camera logging dependencies
  build.ps1                           Compile without Maven
  run.ps1                             Compile and launch without Maven
  build.cmd / run.cmd                 Windows launchers
  pom.xml.maven-backup                Preserved old Maven configuration
  schema.sql
  schema.original.sql
  config/database.properties           Local credentials (ignored by Git)
  .run/QR Attendance.run.xml           IntelliJ run configuration
  src/main/java/
    Main.java                         Class QR startup and teacher records views
    Database.java                     JDBC configuration/connection
    AttendanceRepository.java         SQL reads and transactional writes
    QrCodeService.java                QR encoding and decoding
    QrDialog.java                     Image and webcam scanning UI
    MobileAttendanceServer.java       Signed phone links and manual class QR replacement and attendance page
    StudentRecordTable.java           Student records with inline QR thumbnails
  src/test/java/                     QR integration and webcam smoke tests
  src/main/resources/database.properties
  Main.java                           Original, preserved reference
  Main*.class                         Original compiled files, preserved
  target/                             Generated compilation/test files
  out/                                IntelliJ/script build output
```

Run the source under `src/main/java`, not the preserved root `Main.java` or old root `.class` files. Only `src/main/java` is configured as an IntelliJ Java source folder.

MySQL Connector/J is loaded automatically by JDBC from the local JAR on the runtime classpath.


