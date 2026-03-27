package com.android.update;

/**
 * ──────────────────────────────────────────────────────────────
 *  SERVER CONFIGURATION  —  change BASE_URL based on target:
 *
 *  EMULATOR  →  http://10.0.2.2:5000
 *               (10.0.2.2 is Android emulator's alias for host PC)
 *
 *  REAL PHONE (same Wi-Fi as PC)  →  http://192.168.X.X:5000
 *               Replace 192.168.X.X with your PC's LAN IP.
 *               Find it: Windows → ipconfig → "IPv4 Address"
 *               Make sure Windows Firewall allows port 5000.
 * ──────────────────────────────────────────────────────────────
 */
public class ServerConfig {

    // ✅ CHANGE THIS to match your target device:
    public static final String BASE_URL = "http://10.0.2.2:5000";
    //  For real phone: public static final String BASE_URL = "http://192.168.1.X:5000";

    public static String endpoint(String path) {
        return BASE_URL + "/" + path;
    }
}
