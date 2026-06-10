const { Client } = require('pg');

const client = new Client({
  connectionString: 'postgres://travyn_owner:yv1Pj5qNExbB@ep-silent-fire-a5v594x7.us-east-2.aws.neon.tech/travyn?sslmode=require'
});

async function check() {
  try {
    await client.connect();
    
    // Check all tokens
    const resAll = await client.query('SELECT token, is_active FROM sos_tokens ORDER BY created_at DESC LIMIT 10');
    console.log('Recent tokens:');
    resAll.rows.forEach(r => console.log(`Token: ${r.token}, Active: ${r.is_active}`));

    // Check specific token
    const resSpecific = await client.query("SELECT token, is_active FROM sos_tokens WHERE token = 'YCOBkxaS8KnYA2SpKhyB6xvYo9PcWcMFSmSLjrhEI'");
    if (resSpecific.rows.length > 0) {
      console.log(`\nYCOB Token found! Active: ${resSpecific.rows[0].is_active}`);
    } else {
      console.log('\nYCOB Token NOT FOUND in database!');
    }
  } catch (err) {
    console.error('Error:', err);
  } finally {
    await client.end();
  }
}

check();
