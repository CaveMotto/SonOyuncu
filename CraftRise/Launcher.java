import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * MottoSMP Launcher  —  MottoClient ile çalışır
 * Kullanıcı adı: MOTTO_USER ortam değişkeninden, yoksa args[0]
 * Şifre: MOTTO_PASS ortam değişkeninden (AuthMe otomatik login)
 */
public class Launcher {

    public static void main(String[] args) {
        try {
            File currentDir = new File(".").getAbsoluteFile();
            if (currentDir.getName().equals(".")) {
                currentDir = currentDir.getParentFile();
            }

            File javaExe = new File(currentDir, "java\\jdk-x64\\bin\\java.exe");
            if (!javaExe.exists()) {
                System.err.println("[HATA] java\\jdk-x64\\bin\\java.exe bulunamadi!");
                return;
            }

            File nativesDir  = new File(currentDir, "libraries\\natives");
            File gameDir     = currentDir;
            File assetsDir   = new File(currentDir, "assets");
            File librariesDir= new File(currentDir, "libraries");
            File clientJar   = new File(currentDir, "RiseClient_1.8.9.jar");

            if (!clientJar.exists()) {
                System.err.println("[HATA] RiseClient_1.8.9.jar bulunamadi!");
                return;
            }

            // Create required directories
            for (String d : new String[]{"resourcepacks","shaderpacks","saves","screenshots","logs","skins","capes","cache","frames"})
                new File(gameDir, d).mkdirs();

            // Build classpath
            StringBuilder cp = new StringBuilder();
            cp.append(clientJar.getAbsolutePath());
            File[] libs = librariesDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (libs != null) for (File lib : libs)
                cp.append(File.pathSeparator).append(lib.getAbsolutePath());

            // Username — MOTTO_USER env var > args[0] > settings file > "Oyuncu"
            String username = System.getenv("MOTTO_USER");
            if (username == null || username.isEmpty())
                username = (args.length > 0 && !args[0].isEmpty()) ? args[0] : null;
            if (username == null || username.isEmpty()) {
                System.err.println("[HATA] Oyun ici ad verilmedi. MOTTO_USER ortam degiskenini ayarla.");
                return;
            }

            // RAM — env var > args[1] > settings file > "2G"
            String ram = System.getenv("CRAFTRISE_RAM");
            if (ram == null || ram.isEmpty())
                ram = (args.length > 1 && !args[1].isEmpty()) ? args[1] : null;
            if (ram == null || ram.isEmpty()) {
                File settings = new File(currentDir, "ayarlar.txt");
                ram = readSetting(settings, "RAM_MIKTARI", "2G");
            }
            if (!ram.toUpperCase().endsWith("G") && !ram.toUpperCase().endsWith("M")) ram += "G";

            // Server  — always connect to MottoSMP
            String serverHost = "127.0.0.1";
            String serverPort = "25565";

            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║           MottoSMP  Launcher             ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.println("  Oyuncu : " + username);
            System.out.println("  RAM    : " + ram);
            System.out.println("  Sunucu : " + serverHost + ":" + serverPort);
            System.out.println("  Dizin  : " + gameDir.getAbsolutePath());
            System.out.println("  Baslatiliyor...\n");

            // Original client arguments: encrypted username + launcher keys
            String encryptedUsername = aesEncrypt(USER_KEY,
                    username + "###" + System.currentTimeMillis() + "###1");
            String[] tokens = new String[]{
                "--version",      "1.8.9",
                "--accessToken",  "0",
                "--username",     encryptedUsername,
                "--launcherKey",  "I_AM_RISELAUNCHER_NEW",
                "--launcherSecHash", "26Ffcm3VhyUNP8QrI03Jp3jyrRN3lAZpyZXBRqFo6VRJSQmjXU6qaDlFYZRVVhT0",
                "--launcherLang", "0",
                "--gameDir",      gameDir.getAbsolutePath(),
                "--assetsDir",    assetsDir.getAbsolutePath(),
                "--assetIndex",   "1.8"
            };
            String combinedArgs = String.join("###", tokens);
            String encodedArgs  = Base64.getEncoder()
                                        .encodeToString(combinedArgs.getBytes(StandardCharsets.UTF_8));

            List<String> cmd = new ArrayList<>();
            cmd.add(javaExe.getAbsolutePath());
            cmd.add("-noverify");
            cmd.add("-XX:-UseSplitVerifier");
            cmd.add("-Xmx" + ram);
            cmd.add("-Dfile.encoding=UTF-8");
            cmd.add("-Duser.dir=" + currentDir.getAbsolutePath());
            cmd.add("-Dcraftrise.gameDir=" + currentDir.getAbsolutePath());
            cmd.add("-Dcraftrise.server=" + serverHost);
            cmd.add("-Dcraftrise.port=" + serverPort);
            cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
            cmd.add("-cp");
            cmd.add(cp.toString());
            cmd.add("com.craftrise.client.main.MainLauncher");
            cmd.add(encodedArgs);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(currentDir);
            pb.environment().put("APPDATA",     currentDir.getAbsolutePath());
            pb.environment().put("USERPROFILE", currentDir.getAbsolutePath());
            // Pass password for AuthMe auto-login if provided
            String pass = System.getenv("MOTTO_PASS");
            if (pass != null && !pass.isEmpty())
                pb.environment().put("MOTTO_PASS", pass);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            File logFile   = new File(currentDir, "launcher_output.log");
            PrintWriter log = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(logFile, false), StandardCharsets.UTF_8), true);

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                log.println(line);
                log.flush();

                // Client handshake passthrough (keep launcher-client protocol working)
                if (line.startsWith("rise:client:")) {
                    String enc = line.substring("rise:client:".length()).trim();
                    try {
                        writer.write("rise:launcher:" + decodeHandshakePayload(enc) + "\n");
                        writer.flush();
                        log.println("[Launcher] Handshake tamam.");
                    } catch (Exception ex) {
                        log.println("[Launcher] Handshake hatasi: " + ex);
                    }
                }
            }

            int code = process.waitFor();
            System.out.println("Oyun kapandi. Cikis kodu: " + code);
            log.println("Oyun kapandi. Cikis kodu: " + code);
            log.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final String USER_KEY = "2650053489059452";
    private static final String HANDSHAKE_KEY = "1234758145215632";
    private static final String PREFIX = "3ebi2mclmAM7Ao2";
    private static final String SUFFIX = "KweGTngiZOOj9d6";

    private static String aesEncrypt(String key, String plain) throws Exception {
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE,
               new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"));
        return Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    private static String b64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static String decodeHandshakePayload(String payload) throws Exception {
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(javax.crypto.Cipher.DECRYPT_MODE,
               new javax.crypto.spec.SecretKeySpec(HANDSHAKE_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        String s = new String(c.doFinal(Base64.getDecoder().decode(payload)), StandardCharsets.UTF_8);
        s = b64(b64(s));
        if (s.startsWith(PREFIX) && s.endsWith(SUFFIX))
            s = s.substring(PREFIX.length(), s.length() - SUFFIX.length());
        return b64(b64(s));
    }

    private static String readSetting(File file, String key, String defaultVal) {
        if (!file.exists()) return defaultVal;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase(key))
                    return line.substring(eq + 1).trim();
            }
        } catch (IOException ignored) {}
        return defaultVal;
    }
}
