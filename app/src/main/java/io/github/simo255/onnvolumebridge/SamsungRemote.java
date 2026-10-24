package io.github.simo255.onnvolumebridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Pinned, paired Samsung LAN remote. All networking runs away from the UI thread. */
public final class SamsungRemote {
    private static final String TAG = "OnnVolumeBridge";
    private static final int MAX_FRAME = 65536;
    private static final long KEY_LIFETIME_MS = 1000;
    private static volatile SamsungRemote instance;
    private static volatile String lastStatus = "Not connected";
    private final SharedPreferences prefs;
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), r -> {
        Thread t = new Thread(r, "OnnVolumeBridge-connect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final SecureRandom random = new SecureRandom();
    private final Object outputLock = new Object();
    private final ArrayDeque<PendingKey> pending = new ArrayDeque<>();
    private volatile SSLSocket socket;
    private volatile boolean connected;
    private boolean connecting;
    private long generation;

    private SamsungRemote(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("bridge", Context.MODE_PRIVATE);
    }

    public static SamsungRemote get(Context context) {
        if (instance == null) {
            synchronized (SamsungRemote.class) {
                if (instance == null) instance = new SamsungRemote(context);
            }
        }
        return instance;
    }

    public String host() { return prefs.getString("host", ""); }
    public boolean ready() { return connected; }
    public static String status() { return lastStatus; }

    /** Host must be a private/local IPv4 literal; pin is SHA-256 of the approved leaf certificate. */
    public void configure(String host, String pin, String token) {
        if (!localAddress(host)) throw new IllegalArgumentException("A local IPv4 address is required");
        if (pin == null || !pin.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A SHA-256 certificate pin is required");
        }
        disconnect();
        prefs.edit().putString("host", host).putString("cert_pin", pin.toLowerCase(Locale.ROOT))
                .putString("samsung_token", SecretStore.seal(token == null ? "" : token)).apply();
        state("Configured");
    }

    public synchronized void connect() {
        if (connected || connecting) return;
        String host = host();
        String pin = prefs.getString("cert_pin", "");
        if (!localAddress(host) || !pin.matches("(?i)[0-9a-f]{64}")) {
            state("Configure TV address and certificate first");
            return;
        }
        connecting = true;
        long attempt = ++generation;
        String token = SecretStore.read(prefs, "samsung_token");
        try { worker.execute(() -> open(host, pin, token, attempt)); }
        catch (RejectedExecutionException ignored) { connecting = false; state("Connection busy; try again"); }
    }

    /** Accepts only KEY_VOLUP, KEY_VOLDOWN and KEY_MUTE. Stale presses are discarded. */
    public void key(String key) {
        if (!"KEY_VOLUP".equals(key) && !"KEY_VOLDOWN".equals(key) && !"KEY_MUTE".equals(key)) return;
        synchronized (this) {
            while (pending.size() >= 6) pending.removeFirst();
            pending.addLast(new PendingKey(key, SystemClock.elapsedRealtime()));
        }
        if (ready()) scheduleFlush();
        else connect();
    }

    public synchronized void disconnect() {
        generation++;
        connected = false;
        connecting = false;
        pending.clear();
        SSLSocket old = socket;
        socket = null;
        close(old);
        state("Disconnected");
    }

    private void open(String host, String pin, String token, long attempt) {
        SSLSocket candidate = null;
        Socket raw = null;
        try {
            synchronized (this) { if (attempt != generation) return; }
            state("Connecting");
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(null, new TrustManager[]{new PinTrust(pin)}, random);
            raw = new Socket();
            raw.connect(new InetSocketAddress(host, 8002), 5000);
            raw.setSoTimeout(10000);
            candidate = (SSLSocket) tls.getSocketFactory().createSocket(raw, host, 8002, true);
            candidate.setSoTimeout(10000);
            candidate.startHandshake();
            handshake(candidate, host, token);
            synchronized (this) {
                if (attempt != generation) { close(candidate); return; }
                socket = candidate;
            }
            state("Waiting for TV approval");
            final SSLSocket active = candidate;
            Thread reader = new Thread(() -> readLoop(active, attempt), "OnnVolumeBridge-read");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception ignored) {
            close(candidate);
            close(raw);
            synchronized (this) {
                if (attempt == generation) {
                    connecting = false;
                    connected = false;
                    socket = null;
                    pending.clear();
                    state("Connection failed; check TV address, certificate and network");
                }
            }
        }
    }

    private void handshake(SSLSocket target, String host, String token) throws Exception {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
        String name = Base64.encodeToString("onn Volume".getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String path = "/api/v2/channels/samsung.remote.control?name=" + URLEncoder.encode(name, "UTF-8");
        if (!token.isEmpty()) path += "&token=" + URLEncoder.encode(token, "UTF-8");
        String request = "GET " + path + " HTTP/1.1\r\nHost: " + host + ":8002\r\n"
                + "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n\r\n";
        target.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        target.getOutputStream().flush();
        InputStream input = target.getInputStream();
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int tail = 0;
        while (headers.size() < 16384) {
            int b = input.read();
            if (b < 0) throw new EOFException();
            headers.write(b);
            tail = (tail << 8) | b;
            if (tail == 0x0d0a0d0a) break;
        }
        String response = headers.toString("US-ASCII");
        String expected = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)), Base64.NO_WRAP);
        if (!response.startsWith("HTTP/1.1 101 ") || !response.endsWith("\r\n\r\n")) {
            throw new IllegalStateException("WebSocket upgrade failed");
        }
        boolean accepted = false;
        for (String line : response.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase("Sec-WebSocket-Accept")) {
                accepted = line.substring(colon + 1).trim().equals(expected);
            }
        }
        if (!accepted) throw new IllegalStateException("Invalid WebSocket acceptance");
    }

    private void readLoop(SSLSocket target, long attempt) {
        try {
            // Permit the approval prompt to remain visible, then idle without periodic polling.
            target.setSoTimeout(60000);
            InputStream input = target.getInputStream();
            ByteArrayOutputStream fragment = null;
            while (isCurrent(target, attempt)) {
                int first = readByte(input), second = readByte(input);
                if ((first & 0x70) != 0 || (second & 0x80) != 0) throw new IllegalStateException("Invalid frame");
                int opcode = first & 15;
                boolean fin = (first & 128) != 0;
                long length = second & 127;
                if (length == 126) length = (readByte(input) << 8) | readByte(input);
                else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) {
                        int b = readByte(input);
                        if (i == 0 && b != 0) throw new IllegalStateException("Oversize frame");
                        length = (length << 8) | b;
                        if (length > MAX_FRAME) throw new IllegalStateException("Oversize frame");
                    }
                }
                if (length > MAX_FRAME || (opcode >= 8 && (!fin || length > 125))) {
                    throw new IllegalStateException("Oversize frame");
                }
                byte[] body = new byte[(int) length];
                for (int offset = 0; offset < body.length;) {
                    int count = input.read(body, offset, body.length - offset);
                    if (count < 0) throw new EOFException();
                    offset += count;
                }
                if (opcode == 8) { sendFrame(target, 8, body); break; }
                if (opcode == 9) { sendFrame(target, 10, body); continue; }
                if (opcode == 10) continue;
                if (opcode == 1) {
                    if (fragment != null) throw new IllegalStateException("Interleaved messages");
                    if (fin) message(target, attempt, body);
                    else { fragment = new ByteArrayOutputStream(); fragment.write(body); }
                } else if (opcode == 0 && fragment != null) {
                    if (fragment.size() + body.length > MAX_FRAME) throw new IllegalStateException("Oversize message");
                    fragment.write(body);
                    if (fin) { message(target, attempt, fragment.toByteArray()); fragment = null; }
                } else throw new IllegalStateException("Unsupported frame");
            }
        } catch (Exception ignored) {
            // Never log payloads, IP addresses, certificate fingerprints or pairing tokens.
        } finally {
            close(target);
            synchronized (this) {
                if (isCurrent(target, attempt)) {
                    socket = null;
                    connected = false;
                    connecting = false;
                    pending.clear();
                    state("Disconnected; next volume press reconnects");
                }
            }
        }
    }

    private void message(SSLSocket target, long attempt, byte[] payload) throws Exception {
        JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        String event = json.optString("event");
        synchronized (this) {
            if (!isCurrent(target, attempt)) return;
            if ("ms.channel.connect".equals(event)) {
                JSONObject data = json.optJSONObject("data");
                String token = data == null ? "" : data.optString("token", "");
                if (!token.isEmpty()) prefs.edit().putString("samsung_token", SecretStore.seal(token)).apply();
                connected = true;
                connecting = false;
                target.setSoTimeout(0);
                state("Connected");
                scheduleFlush();
            } else if ("ms.channel.unauthorized".equals(event) || "ms.channel.timeOut".equals(event)) {
                state("TV approval required");
                close(target);
            }
        }
    }

    private void scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try { flushKeys(); }
                finally {
                    flushScheduled.set(false);
                    synchronized (SamsungRemote.this) {
                        if (connected && !pending.isEmpty()) scheduleFlush();
                    }
                }
            });
        } catch (RejectedExecutionException ignored) { flushScheduled.set(false); }
    }

    private void flushKeys() {
        while (true) {
            PendingKey next;
            SSLSocket target;
            synchronized (this) {
                if (!connected || pending.isEmpty()) return;
                next = pending.removeFirst();
                target = socket;
            }
            if (SystemClock.elapsedRealtime() - next.created > KEY_LIFETIME_MS) continue;
            try {
                JSONObject params = new JSONObject().put("Cmd", "Click").put("DataOfCmd", next.key)
                        .put("Option", "false").put("TypeOfRemote", "SendRemoteKey");
                JSONObject json = new JSONObject().put("method", "ms.remote.control").put("params", params);
                sendFrame(target, 1, json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                close(target);
                synchronized (this) {
                    if (socket == target) {
                        connected = false;
                        connecting = false;
                        pending.clear();
                    }
                }
                return;
            }
        }
    }

    private void sendFrame(SSLSocket target, int opcode, byte[] payload) throws Exception {
        if (target == null || payload.length > MAX_FRAME) throw new IllegalStateException("No connection");
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        ByteArrayOutputStream packet = new ByteArrayOutputStream(payload.length + 14);
        packet.write(128 | opcode);
        if (payload.length < 126) packet.write(128 | payload.length);
        else if (payload.length <= 65535) {
            packet.write(128 | 126); packet.write(payload.length >>> 8); packet.write(payload.length);
        } else {
            packet.write(128 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) packet.write((int) (((long) payload.length) >>> shift));
        }
        packet.write(mask);
        for (int i = 0; i < payload.length; i++) packet.write(payload[i] ^ mask[i % 4]);
        synchronized (outputLock) {
            OutputStream output = target.getOutputStream();
            output.write(packet.toByteArray());
            output.flush();
        }
    }

    private synchronized boolean isCurrent(SSLSocket target, long attempt) {
        return generation == attempt && socket == target;
    }

    private void state(String value) {
        lastStatus = value;
        prefs.edit().putString("status", value).apply();
        Log.i(TAG, value);
    }

    private static int readByte(InputStream input) throws Exception {
        int value = input.read();
        if (value < 0) throw new EOFException();
        return value;
    }

    private static void close(java.io.Closeable value) {
        if (value != null) try { value.close(); } catch (Exception ignored) { }
    }

    private static boolean localAddress(String host) {
        if (host == null || !host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return false;
        String[] parts = host.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].length() > 1 && parts[i].startsWith("0")) return false;
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }
        return octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 169 && octets[1] == 254);
    }

    private static final class PendingKey {
        final String key;
        final long created;
        PendingKey(String key, long created) { this.key = key; this.created = created; }
    }

    @android.annotation.SuppressLint("CustomX509TrustManager")
    private static final class PinTrust implements X509TrustManager {
        private final String expected;
        PinTrust(String expected) { this.expected = expected; }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates unsupported");
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                if (chain == null || chain.length == 0) throw new CertificateException("Missing certificate");
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());
                StringBuilder hex = new StringBuilder(64);
                for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
                if (!hex.toString().equalsIgnoreCase(expected)) throw new CertificateException("Certificate changed");
            } catch (CertificateException error) { throw error; }
            catch (Exception error) { throw new CertificateException("Certificate verification failed", error); }
        }
    }
}
