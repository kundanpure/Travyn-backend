const https = require('https');
const http = require('http');

const TOKEN = '8909199719:AAH4mgKCKslEKPzwhmvpf5M5TjzUP97LaPM';
let lastUpdateId = 0;

console.log("Polling Telegram for /start commands...");

setInterval(() => {
    https.get(`https://api.telegram.org/bot${TOKEN}/getUpdates?offset=${lastUpdateId + 1}&timeout=5`, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
            try {
                const json = JSON.parse(data);
                if (!json.ok || !json.result) return;
                
                for (const update of json.result) {
                    lastUpdateId = update.update_id;
                    if (update.message && update.message.text && update.message.text.startsWith('/start')) {
                        console.log(`Received command: ${update.message.text}`);
                        
                        // Forward to Spring Boot webhook
                        const reqData = JSON.stringify({ message: update.message });
                        const req = http.request({
                            hostname: 'localhost',
                            port: 8080,
                            path: '/api/v1/public/telegram/webhook',
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'Content-Length': reqData.length
                            }
                        }, (resp) => {
                            console.log(`Forwarded to Spring Boot, status: ${resp.statusCode}`);
                        });
                        
                        req.on('error', (e) => console.error(`Problem forwarding: ${e.message}`));
                        req.write(reqData);
                        req.end();
                    }
                }
            } catch(e) {}
        });
    }).on('error', (e) => console.error(e));
}, 2000);
