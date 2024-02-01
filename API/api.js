const express = require('express');
const crypto = require('crypto');
const sqlite3 = require('sqlite3').verbose();
const bodyParser = require('body-parser');
const { v4: uuidv4 } = require('uuid');

const app = express();
const port = 3000;

app.use(bodyParser.json());

// SQLite Database Initialization
const db = new sqlite3.Database('url_shortener.db');

db.serialize(() => {
  db.run('CREATE TABLE IF NOT EXISTS url_mappings (short_code TEXT PRIMARY KEY, original_url TEXT)');
  db.run('CREATE TABLE IF NOT EXISTS analytics (id INTEGER PRIMARY KEY AUTOINCREMENT, short_code TEXT, timestamp TEXT, ip_address TEXT)');
});

const generateShortCode = (originalUrl) => {
  const hash = crypto.createHash('sha256');
  hash.update(originalUrl);
  return hash.digest('hex').substring(0, 8); // Using the first 8 characters for simplicity
};

const checkCollision = async (shortCode) => {
  return new Promise((resolve, reject) => {
    db.get('SELECT * FROM url_mappings WHERE short_code=?', [shortCode], (err, row) => {
      if (err) {
        reject(err);
      } else {
        resolve(row !== undefined);
      }
    });
  });
};

const generateUniqueShortCode = async (originalUrl) => {
  let shortCode;
  do {
    shortCode = generateShortCode(originalUrl);
  } while (await checkCollision(shortCode));

  return shortCode;
};

app.get('/', (req, res) => {
  res.send('Welcome to the URL Shortener Service');
});

app.post('/shorten', async (req, res) => {
  const { original_url } = req.body;

  if (!original_url) {
    return res.status(400).json({ error: 'Missing original_url parameter' });
  }

  const shortCode = await generateUniqueShortCode(original_url);

  db.run('INSERT INTO url_mappings VALUES (?, ?)', [shortCode, original_url], (err) => {
    if (err) {
      return res.status(500).json({ error: 'Internal server error' });
    }

    const shortUrl = `${req.protocol}://${req.get('host')}/${shortCode}`;
    res.json({ short_url: shortUrl });
  });
});

app.get('/:shortCode', (req, res) => {
  const { shortCode } = req.params;

  db.get('SELECT original_url FROM url_mappings WHERE short_code=?', [shortCode], (err, row) => {
    if (err) {
      return res.status(500).json({ error: 'Internal server error' });
    }

    if (row) {
      const originalUrl = row.original_url;

      // Log analytics data
      const timestamp = new Date().toISOString();
      const ip_address = req.ip;

      db.run('INSERT INTO analytics (short_code, timestamp, ip_address) VALUES (?, ?, ?)',
        [shortCode, timestamp, ip_address], (err) => {
          if (err) {
            console.error(err);
          }
        });

      return res.redirect(301, originalUrl);
    } else {
      return res.status(404).json({ error: 'Short URL not found' });
    }
  });
});

app.get('/analytics/:shortCode', (req, res) => {
  const { shortCode } = req.params;

  db.all('SELECT timestamp, ip_address FROM analytics WHERE short_code=?', [shortCode], (err, rows) => {
    if (err) {
      return res.status(500).json({ error: 'Internal server error' });
    }

    if (rows.length > 0) {
      const analyticsData = rows.map(row => ({ timestamp: row.timestamp, ip_address: row.ip_address }));
      res.json(analyticsData);
    } else {
      return res.status(404).json({ error: 'No analytics data found for the given short code' });
    }
  });
});

app.listen(port, () => {
  console.log(`Server is running on port ${port}`);
});
