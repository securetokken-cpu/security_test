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

    // ✅ REVERTED to the original ngrok link (as requested)
    public static final String BASE_URL = "https://sophistic-monocular-magdalena.ngrok-free.dev";

    public static String endpoint(String path) {
        // Ensure we don't end up with double slashes if path starts with one
        String base = BASE_URL;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String p = path;
        if (p.startsWith("/")) p = p.substring(1);
        
        return base + "/" + p;
    }
}
