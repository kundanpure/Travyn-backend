const https = require('https');
const http = require('http');

// Script to poll Telegram updates locally and forward to localhost Webhook
// NOTE: DO NOT HARDCODE YOUR TOKEN HERE IF YOU COMMIT THIS FILE.

const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN; 

if (!TELEGRAM_BOT_TOKEN) {
  console.error("Please set TELEGRAM_BOT_TOKEN environment variable");
  process.exit(1);
}

const TELEGRAM_API = `https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}`;
let lastUpdateId = 0;

console.log("Polling Telegram for commands...");

setInterval(() => {
    https.get(`${TELEGRAM_API}/getUpdates?offset=${lastUpdateId + 1}&timeout=5`, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
            try {
                const response = JSON.parse(data);
                if (response.ok && response.result.length > 0) {
                    for (const update of response.result) {
                        lastUpdateId = update.update_id;
                        console.log("Received update:", update.update_id);
                        
                        // Forward to local webhook
                        const req = http.request({
                            hostname: 'localhost',
                            port: 8080,
                            path: '/api/v1/public/telegram/webhook',
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' }
                        }, (webhookRes) => {
                            console.log(`Webhook forwarded, status: ${webhookRes.statusCode}`);
                        });
                        req.on('error', (e) => console.error("Webhook forwarding failed:", e.message));
                        req.write(JSON.stringify(update));
                        req.end();
                    }
                }
            } catch (e) {
                console.error("Error parsing update:", e.message);
            }
        });
    }).on('error', e => console.error("Polling error:", e.message));
}, 2000);
