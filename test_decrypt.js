const crypto = require('crypto');

const key = Buffer.from('TravynLocationAES256KeyMustBe32!', 'utf8');
const dbData = 'hb9Dsqpdxj2E6LpnemPgAQywqh88pynqr15YK8iSnj3bJLnK6XtoafhORkqAhg==';

try {
    const decoded = Buffer.from(dbData, 'base64');
    const iv = decoded.subarray(0, 12);
    const ciphertext = decoded.subarray(12, decoded.length - 16);
    const authTag = decoded.subarray(decoded.length - 16);

    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);
    
    let plaintext = decipher.update(ciphertext, null, 'utf8');
    plaintext += decipher.final('utf8');
    
    console.log("Decrypted successfully:", plaintext);
} catch (e) {
    console.error("Failed to decrypt:", e.message);
}
