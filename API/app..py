from flask import Flask, request, redirect, jsonify
import hashlib
import sqlite3
import string
import random
from datetime import datetime

app = Flask(__name__)

# SQLite Database Initialization
conn = sqlite3.connect('url_shortener.db', check_same_thread=False)
cursor = conn.cursor()
cursor.execute('CREATE TABLE IF NOT EXISTS url_mappings (short_code TEXT PRIMARY KEY, original_url TEXT)')
cursor.execute('CREATE TABLE IF NOT EXISTS analytics (id INTEGER PRIMARY KEY AUTOINCREMENT, short_code TEXT, timestamp TEXT, ip_address TEXT)')
conn.commit()

def generate_short_code(original_url):
    # Using SHA-256 hash as a simple way to generate a unique short code
    hash_object = hashlib.sha256(original_url.encode())
    short_code = hash_object.hexdigest()[:8]  # Using the first 8 characters for simplicity
    return short_code

def check_collision(short_code):
    cursor.execute('SELECT * FROM url_mappings WHERE short_code=?', (short_code,))
    return cursor.fetchone() is not None

def generate_unique_short_code(original_url):
    while True:
        short_code = generate_short_code(original_url)
        if not check_collision(short_code):
            return short_code

@app.route('/')
def index():
    return 'Welcome to the URL Shortener Service'

@app.route('/shorten', methods=['POST'])
def shorten_url():
    data = request.get_json()

    if 'original_url' not in data:
        return 'Missing original_url parameter', 400

    original_url = data['original_url']
    short_code = generate_unique_short_code(original_url)

    cursor.execute('INSERT INTO url_mappings VALUES (?, ?)', (short_code, original_url))
    conn.commit()

    short_url = request.host_url + short_code
    return {'short_url': short_url}

@app.route('/<short_code>')
def redirect_to_original_url(short_code):
    cursor.execute('SELECT original_url FROM url_mappings WHERE short_code=?', (short_code,))
    result = cursor.fetchone()

    if result:
        original_url = result[0]

        # Log analytics data
        timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        ip_address = request.remote_addr

        cursor.execute('INSERT INTO analytics (short_code, timestamp, ip_address) VALUES (?, ?, ?)',
                       (short_code, timestamp, ip_address))
        conn.commit()

        return redirect(original_url, code=301)
    else:
        return 'Short URL not found', 404

@app.route('/analytics/<short_code>')
def get_analytics(short_code):
    cursor.execute('SELECT timestamp, ip_address FROM analytics WHERE short_code=?', (short_code,))
    results = cursor.fetchall()

    if results:
        analytics_data = [{'timestamp': timestamp, 'ip_address': ip_address} for timestamp, ip_address in results]
        return jsonify(analytics_data)
    else:
        return 'No analytics data found for the given short code', 404

if __name__ == '__main__':
    app.run(debug=True)
