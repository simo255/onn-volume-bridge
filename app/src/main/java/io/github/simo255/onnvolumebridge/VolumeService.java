package io.github.simo255.onnvolumebridge;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Keeps local relay connections alive and intercepts mute; never changes Android volume. */
public final class VolumeService extends AccessibilityService {
    private AudioManager audio;
    private SharedPreferences prefs;
    private InputManager input;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (p, key) -> {
        if ("config_revision".equals(key)) LocalAdbVolumeKeys.get(this).stop();
        if ("enabled".equals(key) || "raw_monitor".equals(key) || "config_revision".equals(key)) handler.post(this::syncState);
    };
    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { syncState(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { syncState(); }
    };
    private final InputManager.InputDeviceListener inputListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { syncState(); }
        @Override public void onInputDeviceChanged(int id) { syncState(); }
        @Override public void onInputDeviceRemoved(int id) { }
    };

    @Override protected void onServiceConnected() {
        Log.i("OnnVolumeBridge", "Volume key service connected");
        prefs = getSharedPreferences("bridge", MODE_PRIVATE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
        audio.registerAudioDeviceCallback(deviceCallback, handler);
        input = (InputManager) getSystemService(INPUT_SERVICE);
        input.registerInputDeviceListener(inputListener, handler);
        syncState();
    }

    private void syncState() {
        if (prefs == null) return;
        boolean hdmi = hdmiOutput();
        prefs.edit().putBoolean("raw_monitor_route_hdmi", hdmi).apply();
        if (!hdmi) LocalAdbVolumeKeys.get(this).cancelHold();
        boolean enabled = prefs.getBoolean("enabled", false) && VolumeRelay.get(this).configured();
        if (enabled) VolumeRelay.get(this).connect();
        else VolumeRelay.get(this).disconnectUnlessSetup();
        if (enabled && prefs.getBoolean("raw_monitor", true)) LocalAdbVolumeKeys.get(this).start();
        else LocalAdbVolumeKeys.get(this).stop();
    }

    private boolean hdmiOutput() {
        if (audio == null) return false;
        boolean hdmi = false;
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_HDMI || type == AudioDeviceInfo.TYPE_HDMI_ARC
                    || type == AudioDeviceInfo.TYPE_HDMI_EARC) hdmi = true;
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE
                    || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) return false;
        }
        return hdmi;
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (prefs == null || !prefs.getBoolean("enabled", false) || !hdmiOutput()) return false;
        VolumeKey key;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (prefs.getBoolean("raw_monitor", true)) return false;
                key = VolumeKey.UP; break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (prefs.getBoolean("raw_monitor", true)) return false;
                key = VolumeKey.DOWN; break;
            case KeyEvent.KEYCODE_VOLUME_MUTE: key = VolumeKey.MUTE; break;
            default: return false;
        }
        if (!VolumeRelay.get(this).configured() || !VolumeRelay.get(this).ready()) { VolumeRelay.get(this).connect(); return false; }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            VolumeRelay.get(this).key(key);
            prefs.edit().putLong("last_key_at", System.currentTimeMillis())
                .putInt("key_count", prefs.getInt("key_count", 0) + 1).apply();
            Log.i("OnnVolumeBridge", "Volume command received: " + key);
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (audio != null) audio.unregisterAudioDeviceCallback(deviceCallback);
        if (input != null) input.unregisterInputDeviceListener(inputListener);
        handler.removeCallbacksAndMessages(null);
        LocalAdbVolumeKeys.get(this).stop();
        VolumeRelay.get(this).disconnect();
        super.onDestroy();
    }
}
