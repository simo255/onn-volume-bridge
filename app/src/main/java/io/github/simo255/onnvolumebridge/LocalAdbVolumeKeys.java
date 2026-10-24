package io.github.simo255.onnvolumebridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Authenticated loopback-only ADB reader. No PC, root, injected keys or audio volume changes. */
public final class LocalAdbVolumeKeys {
    private static final String TAG = "OnnVolumeBridge";
    private static final int CNXN = command("CNXN"), AUTH = command("AUTH"), OPEN = command("OPEN");
    private static final int OKAY = command("OKAY"), WRTE = command("WRTE"), CLSE = command("CLSE");
    private static final int VERSION = 0x01000000;
    private static final int MAX_PACKET = 1024 * 1024;
    private static final String KEY_FILE = "local-adb-key.json";
    private static final String WRAP_ALIAS = "onn_volume_bridge_local_adb_wrap";
    private static final byte[] SHA1_PREFIX = new byte[]{0x30, 0x21, 0x30, 0x09, 0x06, 0x05,
            0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
    private static final Pattern DEVICE = Pattern.compile("(?m)^add device \\d+: (/dev/input/event[0-9]+)\\s*$");
    private static final Pattern KEY = Pattern.compile("\\b(KEY_VOLUMEUP|KEY_VOLUMEDOWN)\\s+(DOWN|UP|REPEAT|0000000[012])\\b");
    private static volatile LocalAdbVolumeKeys instance;
    private static volatile String lastStatus = "Not connected";
    private final Context context;
    private final SharedPreferences prefs;
    private volatile Socket activeSocket;
    private volatile boolean ready;
    private boolean running;
    private long generation;
    private Thread thread;
    private final ScheduledThreadPoolExecutor repeatWorker;
    private final VolumeHoldRepeater hold;

    private LocalAdbVolumeKeys(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("bridge", Context.MODE_PRIVATE);
        repeatWorker = new ScheduledThreadPoolExecutor(1, task -> {
            Thread worker = new Thread(task, "OnnVolumeBridge-volume-hold");
            worker.setDaemon(true); return worker;
        });
        repeatWorker.setRemoveOnCancelPolicy(true);
        hold = new VolumeHoldRepeater((task, delay) -> {
            java.util.concurrent.ScheduledFuture<?> future = repeatWorker.schedule(task, delay, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
            () -> ready && enabled() && prefs.getBoolean("raw_monitor_route_hdmi", true)
                && VolumeRelay.get(this.context).ready(), this::emitVolume);
        hold.setNativeHold(new VolumeHoldRepeater.NativeHold() {
            public boolean start(VolumeKey key) { return VolumeRelay.get(LocalAdbVolumeKeys.this.context).startHold(key); }
            public void stop() { VolumeRelay.get(LocalAdbVolumeKeys.this.context).endHold(); }
        });

    }

    public static LocalAdbVolumeKeys get(Context context) {
        if (instance == null) synchronized (LocalAdbVolumeKeys.class) {
            if (instance == null) instance = new LocalAdbVolumeKeys(context);
        }
        return instance;
    }

    public boolean ready() { return ready; }
    public static String status() { return lastStatus; }

    public synchronized void start() {
        if (running || !enabled()) return;
        running = true;
        ready = false;
        long run = ++generation;
        thread = new Thread(() -> monitor(run), "OnnVolumeBridge-local-adb");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        ready = false;
        generation++;
        hold.stop();
        close(activeSocket);
        activeSocket = null;
        if (thread != null) thread.interrupt();
        thread = null;
        state("Local key monitor stopped");
    }

    private boolean enabled() {
        return prefs.getBoolean("enabled", false) && prefs.getBoolean("raw_monitor", true);
    }

    private synchronized boolean current(long run) {
        return running && run == generation && enabled();
    }

    private void monitor(long run) {
        try {
            AdbKey privateKey = loadKey();
            for (int attempt = 0; attempt < 3 && current(run); attempt++) {
                Session session = null;
                try {
                    state("Connecting local key monitor");
                    Socket socket = new Socket();
                    synchronized (this) {
                        if (!current(run)) { close(socket); return; }
                        activeSocket = socket;
                    }
                    socket.connect(new InetSocketAddress("127.0.0.1", 5555), 4000);
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(120000);
                    session = new Session(socket);
                    session.authenticate(privateKey);
                    if (!current(run)) return;
                    socket.setSoTimeout(12000);
                    String inventory = session.shellOutput("getevent -pl", 128 * 1024);
                    String path = selectRemote(inventory);
                    if (path == null) {
                        state("Onn keyboard volume input not found");
                        return;
                    }
                    socket.setSoTimeout(0);
                    session.open("getevent -ql " + path);
                    if (!current(run)) return;
                    synchronized (this) {
                        if (!current(run)) return;
                        ready = true;
                        hold.stop();
                    }
                    state("Local volume key monitor connected");
                    ByteArrayOutputStream line = new ByteArrayOutputStream();
                    while (current(run)) {
                        byte[] bytes = session.readStream();
                        if (bytes == null) throw new EOFException();
                        for (byte value : bytes) {
                            int b = value & 255;
                            if (b == '\n') {
                                volumeLine(line.toString("UTF-8"), run);
                                line.reset();
                            } else if (line.size() < 2048) line.write(b);
                            else throw new IllegalStateException("Input line too large");
                        }
                    }
                } catch (AuthenticationNeeded error) {
                    // Do not repeatedly create permission prompts when this key has not been approved.
                    if (current(run)) state("Approve USB debugging for onn Volume on this box");
                    return;
                } catch (Exception ignored) {
                    if (!current(run)) return;
                    diagnostic("Local transport", ignored);
                    ready = false;
                    state(attempt == 2 ? "Local ADB unavailable; toggle relay to retry"
                            : "Local key monitor reconnecting");
                    if (attempt < 2) {
                        try { Thread.sleep(700L * (attempt + 1)); }
                        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return; }
                    }
                } finally {
                    if (session != null) close(session.socket);
                    synchronized (this) {
                        if (run == generation) {
                            hold.stop();
                            close(activeSocket);
                            activeSocket = null;
                            ready = false;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            if (current(run)) {
                diagnostic("Local private key", ignored);
                state("Local ADB key unavailable (" + ignored.getClass().getSimpleName() + ")");
            }
        } finally {
            synchronized (this) {
                if (run == generation) { running = false; ready = false; thread = null; }
            }
        }
    }

    private synchronized void volumeLine(String line, long run) {
        if (!current(run)) return;
        Matcher match = KEY.matcher(line);
        if (!match.find()) return;
        VolumeKey key = "KEY_VOLUMEUP".equals(match.group(1)) ? VolumeKey.UP : VolumeKey.DOWN;
        String action = match.group(2);
        if ("UP".equals(action) || "00000000".equals(action)) { hold.up(key); return; }
        if (!"DOWN".equals(action) && !"00000001".equals(action)) return;
        if (!prefs.getBoolean("raw_monitor_route_hdmi", true)) { hold.stop(); return; }
        if (!VolumeRelay.get(context).ready()) {
            hold.stop();
            VolumeRelay.get(context).key(key); // One reconnecting tap, never a queued hold burst.
            return;
        }
        hold.down(key);
    }

    public void cancelHold() { hold.stop(); }

    private void emitVolume(VolumeKey key) {
        VolumeRelay.get(context).key(key);
        prefs.edit().putLong("last_key_at", System.currentTimeMillis())
                .putInt("key_count", prefs.getInt("key_count", 0) + 1)
                .putInt("raw_key_count", prefs.getInt("raw_key_count", 0) + 1).apply();
        Log.i(TAG, "Local volume command sent: " + key);
    }

    private String selectRemote(String inventory) {
        return InputDeviceSelector.select(inventory, prefs.getString("input_name", ""));
    }

    private AdbKey loadKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(WRAP_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(WRAP_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            generator.generateKey();
        }
        SecretKey wrapping = (SecretKey) store.getKey(WRAP_ALIAS, null);
        byte[] privateBytes;
        if (context.getFileStreamPath(KEY_FILE).exists()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = context.openFileInput(KEY_FILE)) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (bytes.size() + count > 16384) throw new IllegalStateException("Oversize key file");
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject saved = new JSONObject(bytes.toString("UTF-8"));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, wrapping,
                    new GCMParameterSpec(128, Base64.decode(saved.getString("iv"), Base64.NO_WRAP)));
            privateBytes = cipher.doFinal(Base64.decode(saved.getString("key"), Base64.NO_WRAP));
        } else {
            KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
            rsa.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
            KeyPair pair = rsa.generateKeyPair();
            privateBytes = pair.getPrivate().getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrapping);
            JSONObject saved = new JSONObject().put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .put("key", Base64.encodeToString(cipher.doFinal(privateBytes), Base64.NO_WRAP));
            try (OutputStream output = context.openFileOutput(KEY_FILE, Context.MODE_PRIVATE)) {
                output.write(saved.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            return decodeKey(privateBytes);
        } finally { java.util.Arrays.fill(privateBytes, (byte) 0); }
    }

    static byte[] publicKey(RSAPrivateKey key) {
        return publicKey(key.getModulus(), RSAKeyGenParameterSpec.F4);
    }

    static byte[] publicKey(BigInteger modulus, BigInteger publicExponent) {
        BigInteger base = BigInteger.ONE.shiftLeft(32);
        int inverse = modulus.mod(base).modInverse(base).negate().mod(base).intValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(modulus);
        ByteBuffer encoded = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        encoded.putInt(64).putInt(inverse);
        for (int i = 0; i < 64; i++) encoded.putInt(modulus.shiftRight(i * 32).intValue());
        for (int i = 0; i < 64; i++) encoded.putInt(rr.shiftRight(i * 32).intValue());
        encoded.putInt(publicExponent.intValue());
        String line = Base64.encodeToString(encoded.array(), Base64.NO_WRAP) + " onn-volume@localhost\0";
        return line.getBytes(StandardCharsets.US_ASCII);
    }

    /** Read public n/e from the preserved PKCS8; Conscrypt need not expose a CRT private-key interface. */
    static AdbKey decodeKey(byte[] encoded) throws Exception {
        Der root = new Der(encoded, 0, encoded.length);
        Der pkcs8 = root.read(0x30);
        if (root.remaining() != 0 || !BigInteger.ZERO.equals(pkcs8.integer()))
            throw new IllegalStateException("Unsupported private key container");
        Der algorithm = pkcs8.read(0x30);
        byte[] oid = algorithm.read(0x06).bytes();
        byte[] rsaOid = new byte[]{0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01};
        if (!java.util.Arrays.equals(oid, rsaOid)) throw new IllegalStateException("Non-RSA private key");
        if (algorithm.remaining() != 0 && algorithm.read(0x05).remaining() != 0)
            throw new IllegalStateException("Invalid RSA algorithm parameters");
        if (algorithm.remaining() != 0) throw new IllegalStateException("Invalid RSA algorithm parameters");
        Der octets = pkcs8.read(0x04);
        Der rsa = octets.read(0x30);
        if (octets.remaining() != 0 || !BigInteger.ZERO.equals(rsa.integer()))
            throw new IllegalStateException("Unsupported RSA private key");
        BigInteger modulus = rsa.integer(), exponent = rsa.integer();
        if (modulus.bitLength() != 2048 || !RSAKeyGenParameterSpec.F4.equals(exponent))
            throw new IllegalStateException("Invalid local RSA parameters");
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        if (!(privateKey instanceof RSAPrivateKey)
                || !modulus.equals(((RSAPrivateKey) privateKey).getModulus()))
            throw new IllegalStateException("RSA provider returned a different key");
        return new AdbKey(privateKey, modulus, exponent);
    }

    static final class AdbKey {
        final PrivateKey privateKey;
        final BigInteger modulus, exponent;
        AdbKey(PrivateKey privateKey, BigInteger modulus, BigInteger exponent) {
            this.privateKey = privateKey; this.modulus = modulus; this.exponent = exponent;
        }
    }

    private static final class Der {
        final byte[] data;
        final int end;
        int offset;
        Der(byte[] data, int start, int end) { this.data = data; this.offset = start; this.end = end; }
        int remaining() { return end - offset; }
        Der read(int expectedTag) {
            if (remaining() < 2 || (data[offset++] & 255) != expectedTag)
                throw new IllegalStateException("Invalid DER tag");
            int length = data[offset++] & 255;
            if ((length & 128) != 0) {
                int count = length & 127;
                if (count == 0 || count > 3 || remaining() < count)
                    throw new IllegalStateException("Invalid DER length");
                length = 0;
                for (int i = 0; i < count; i++) length = (length << 8) | (data[offset++] & 255);
            }
            if (length > remaining()) throw new IllegalStateException("Truncated DER value");
            Der value = new Der(data, offset, offset + length);
            offset += length;
            return value;
        }
        byte[] bytes() { return java.util.Arrays.copyOfRange(data, offset, end); }
        BigInteger integer() {
            Der value = read(0x02);
            if (value.remaining() == 0 || (data[value.offset] & 128) != 0)
                throw new IllegalStateException("Invalid DER integer");
            return new BigInteger(value.bytes());
        }
    }

    static byte[] signToken(PrivateKey key, byte[] token) throws Exception {
        if (token.length != 20) throw new IllegalStateException("Invalid authentication challenge");
        byte[] digestInfo = new byte[SHA1_PREFIX.length + token.length];
        System.arraycopy(SHA1_PREFIX, 0, digestInfo, 0, SHA1_PREFIX.length);
        System.arraycopy(token, 0, digestInfo, SHA1_PREFIX.length, token.length);
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, key);
        return rsa.doFinal(digestInfo);
    }

    private void state(String text) {
        lastStatus = text;
        prefs.edit().putString("raw_status", text).apply();
        Log.i(TAG, text);
    }

    /** Exception messages may contain hosts, tokens or key material: log type/location only. */
    private static void diagnostic(String stage, Throwable failure) {
        int causes = 0;
        for (Throwable cause = failure; cause != null && causes < 4; cause = cause.getCause(), causes++) {
            Log.w(TAG, stage + " error type " + cause.getClass().getName());
            int frames = 0;
            for (StackTraceElement frame : cause.getStackTrace()) {
                String owner = frame.getClassName();
                if (owner.startsWith("io.github.simo255.onnvolumebridge.")
                        || owner.startsWith("android.security.")
                        || owner.startsWith("com.android.org.conscrypt.")
                        || owner.startsWith("javax.crypto.") || owner.startsWith("java.security.")) {
                    Log.w(TAG, stage + " location " + owner + "." + frame.getMethodName()
                            + ":" + frame.getLineNumber());
                    if (++frames >= 8) break;
                }
            }
        }
    }

    private static void close(Socket socket) {
        if (socket != null) try { socket.close(); } catch (Exception ignored) { }
    }

    static int command(String text) {
        return text.charAt(0) | (text.charAt(1) << 8) | (text.charAt(2) << 16) | (text.charAt(3) << 24);
    }

    private static final class AuthenticationNeeded extends Exception { }

    private static final class Packet {
        final int command, arg0, arg1;
        final byte[] data;
        Packet(int command, int arg0, int arg1, byte[] data) {
            this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data;
        }
    }

    private final class Session {
        final Socket socket;
        final InputStream input;
        final OutputStream output;
        int local = 0, remote = 0;
        boolean streamClosed;

        Session(Socket socket) throws Exception {
            this.socket = socket;
            input = socket.getInputStream(); output = socket.getOutputStream();
        }

        void authenticate(AdbKey key) throws Exception {
            send(CNXN, VERSION, 4096, "host::\0".getBytes(StandardCharsets.US_ASCII));
            boolean signed = false, prompted = false;
            for (int count = 0; count < 8; count++) {
                Packet packet;
                try { packet = receive(); }
                catch (java.net.SocketTimeoutException timeout) {
                    if (prompted) throw new AuthenticationNeeded();
                    throw timeout;
                }
                if (packet.command == CNXN) return;
                if (packet.command != AUTH || packet.arg0 != 1 || packet.data.length != 20)
                    throw new IllegalStateException("Unexpected authentication response");
                if (!signed) { send(AUTH, 2, 0, signToken(key.privateKey, packet.data)); signed = true; }
                else if (!prompted) {
                    state("Approve USB debugging for onn Volume on this box");
                    send(AUTH, 3, 0, publicKey(key.modulus, key.exponent)); prompted = true;
                } else throw new AuthenticationNeeded();
            }
            throw new AuthenticationNeeded();
        }

        void open(String command) throws Exception {
            local++; remote = 0; streamClosed = false;
            send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            Packet packet = receive();
            if (packet.command == CLSE && packet.arg1 == local) throw new IllegalStateException("Shell refused");
            if (packet.command != OKAY || packet.arg1 != local || packet.arg0 == 0)
                throw new IllegalStateException("Unexpected shell response");
            remote = packet.arg0;
        }

        String shellOutput(String command, int maximum) throws Exception {
            open(command);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (byte[] chunk; (chunk = readStream()) != null;) {
                if (bytes.size() + chunk.length > maximum) throw new IllegalStateException("Shell result too large");
                bytes.write(chunk);
            }
            return bytes.toString("UTF-8");
        }

        byte[] readStream() throws Exception {
            if (streamClosed) return null;
            Packet packet = receive();
            if (packet.arg1 != local || packet.arg0 != remote) throw new IllegalStateException("Unexpected stream");
            if (packet.command == CLSE) {
                send(CLSE, local, remote, new byte[0]); streamClosed = true; return null;
            }
            if (packet.command != WRTE) throw new IllegalStateException("Unexpected stream packet");
            send(OKAY, local, remote, new byte[0]);
            return packet.data;
        }

        void send(int command, int arg0, int arg1, byte[] data) throws Exception {
            int sum = 0;
            for (byte b : data) sum += b & 255;
            ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(command ^ -1);
            output.write(header.array()); output.write(data); output.flush();
        }

        Packet receive() throws Exception {
            byte[] header = readFully(24);
            ByteBuffer parsed = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int command = parsed.getInt(), arg0 = parsed.getInt(), arg1 = parsed.getInt();
            int length = parsed.getInt(), checksum = parsed.getInt(), magic = parsed.getInt();
            if (magic != (command ^ -1) || length < 0 || length > MAX_PACKET)
                throw new IllegalStateException("Invalid ADB frame");
            byte[] data = readFully(length);
            int sum = 0;
            for (byte b : data) sum += b & 255;
            if (sum != checksum) throw new IllegalStateException("ADB checksum mismatch");
            return new Packet(command, arg0, arg1, data);
        }

        byte[] readFully(int size) throws Exception {
            byte[] bytes = new byte[size];
            for (int offset = 0; offset < size;) {
                int count = input.read(bytes, offset, size - offset);
                if (count < 0) throw new EOFException();
                offset += count;
            }
            return bytes;
        }
    }
}
