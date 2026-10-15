import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class Server {
    static final List<String> chat = Collections.synchronizedList(new ArrayList<>());
    static final Path USERS = Path.of("users.csv"); // Excel mein khulta hai
    static final Path ACCOUNTS = Path.of("accounts.txt"); // password hash yahan, Excel mein nahi

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("uploads"));
        HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);
        s.createContext("/", Server::files);
        s.createContext("/api/signup", ex -> {
            Map<String, String> f = form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String name = clean(f.get("name")).trim(), contact = clean(f.get("contact")).trim().toLowerCase(), pass = clean(f.get("pass"));
            if (name.isEmpty() || contact.isEmpty() || pass.length() < 4) { send(ex, 400, "Naam, Gmail/Phone aur 4+ akshar ka password bharo"); return; }
            synchronized (USERS) {
                if (findAccount(contact) != null) { send(ex, 409, "Yeh account pehle se hai, Login karo"); return; }
                String salt = Long.toHexString(new java.security.SecureRandom().nextLong());
                Files.writeString(ACCOUNTS, contact + "\t" + salt + "\t" + hash(salt + pass) + "\t" + name + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                log(name, contact, "Account bana");
            }
            send(ex, 200, name);
        });
        s.createContext("/api/login", ex -> {
            Map<String, String> f = form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String contact = clean(f.get("contact")).trim().toLowerCase(), pass = clean(f.get("pass"));
            synchronized (USERS) {
                String[] a = findAccount(contact);
                if (a == null || !a[2].equals(hash(a[1] + pass))) { send(ex, 401, "Galat Gmail/Phone ya password. Pehle Create Account karo."); return; }
                log(a[3], contact, "Login");
                send(ex, 200, a[3]);
            }
        });
        s.createContext("/api/reset", ex -> {
            Map<String, String> f = form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String contact = clean(f.get("contact")).trim().toLowerCase(), name = clean(f.get("name")).trim(), pass = clean(f.get("pass"));
            synchronized (USERS) {
                String[] a = findAccount(contact);
                if (a == null || !a[3].equalsIgnoreCase(name) || pass.length() < 4) { send(ex, 400, "Gmail/Phone ya naam match nahi hua"); return; }
                String salt = Long.toHexString(new java.security.SecureRandom().nextLong());
                List<String> out = new ArrayList<>();
                for (String l : Files.readAllLines(ACCOUNTS))
                    out.add(l.startsWith(contact + "\t") ? contact + "\t" + salt + "\t" + hash(salt + pass) + "\t" + a[3] : l);
                Files.write(ACCOUNTS, out);
                log(a[3], contact, "Password badla");
            }
            send(ex, 200, "ok");
        });
        s.createContext("/api/library", ex -> { // photos/ aur videos/ folder ki list
            List<String> out = new ArrayList<>();
            for (String dir : new String[]{"photos", "videos"}) {
                Path d = Path.of(dir);
                if (!Files.isDirectory(d)) continue;
                try (var st = Files.list(d)) {
                    st.map(p -> p.getFileName().toString()).filter(n -> n.matches("(?i).+\\.(jpg|jpeg|png|webp|gif|mp4|webm|mov)")).sorted()
                      .forEach(n -> out.add(dir.charAt(0) + ":" + n));
                }
            }
            send(ex, 200, String.join("\n", out));
        });
        s.createContext("/api/chat", ex -> {
            if (ex.getRequestMethod().equals("POST")) {
                Map<String, String> f = form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                chat.add(clean(f.get("user")) + "\t" + clean(f.get("text")) + "\t" + LocalTime.now().withNano(0));
                if (chat.size() > 300) chat.remove(0);
            }
            send(ex, 200, String.join("\n", chat));
        });
        s.createContext("/api/media", ex -> { // sirf list aur upload, DELETE hai hi nahi
            if (ex.getRequestMethod().equals("POST")) {
                String name = new File(URLDecoder.decode(ex.getRequestURI().getQuery().replace("name=", ""), "UTF-8")).getName()
                        .replaceAll("[^a-zA-Z0-9._-]", "_");
                if (!name.matches("(?i).+\\.(jpg|jpeg|png|gif|webp|mp4|webm|mov)")) { send(ex, 400, "bad type"); return; }
                byte[] data = ex.getRequestBody().readNBytes(50 * 1024 * 1024);
                Files.write(Path.of("uploads", System.currentTimeMillis() + "_" + name), data);
                send(ex, 200, "ok");
            } else {
                try (var st = Files.list(Path.of("uploads"))) {
                    send(ex, 200, String.join("\n", st.map(p -> p.getFileName().toString()).sorted(Comparator.reverseOrder()).toList()));
                }
            }
        });
        s.start();
        System.out.println("Chalu hai: http://localhost:8080");
    }

    static String[] findAccount(String contact) throws IOException {
        if (!Files.exists(ACCOUNTS)) return null;
        for (String l : Files.readAllLines(ACCOUNTS)) { String[] a = l.split("\t", 4); if (a.length == 4 && a[0].equals(contact)) return a; }
        return null;
    }

    static String hash(String x) throws IOException {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IOException(e); }
    }

    static void log(String name, String contact, String what) throws IOException {
        if (!Files.exists(USERS)) Files.writeString(USERS, "Name,Gmail/Phone,Kaam,Time\n");
        Files.writeString(USERS, String.join(",", q(name), q(contact), q(what), q(LocalDateTime.now().toString())) + "\n", StandardOpenOption.APPEND);
    }

    static void files(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        Path f = p.equals("/") ? Path.of("index.html") : Path.of("." + p).normalize();
        boolean ok = f.equals(Path.of("index.html")) || f.equals(Path.of("bg.jpg")) || f.equals(Path.of("bg.mp4")) || f.startsWith("uploads") || f.startsWith("songs") || f.startsWith("photos") || f.startsWith("videos");
        if (!ok || !Files.isRegularFile(f)) { send(ex, 404, "not found"); return; }
        String t = f.toString().endsWith(".mp3") ? "audio/mpeg" : f.toString().endsWith(".mp4") ? "video/mp4" : Files.probeContentType(f);
        ex.getResponseHeaders().add("Content-Type", f.toString().endsWith(".html") ? "text/html; charset=utf-8" : (t == null ? "application/octet-stream" : t));
        byte[] b = Files.readAllBytes(f);
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    static void send(HttpExchange ex, int code, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    static Map<String, String> form(String body) throws UnsupportedEncodingException {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            String[] a = kv.split("=", 2);
            m.put(a[0], a.length > 1 ? URLDecoder.decode(a[1], "UTF-8") : "");
        }
        return m;
    }

    static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " "); }

    static String q(String s) { // CSV quote + Excel formula se bachav
        s = clean(s);
        if (s.matches("^[=+\\-@].*")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
