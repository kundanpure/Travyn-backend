const { Client } = require('pg');

const client = new Client({
  connectionString: 'postgres://neondb_owner:npg_nxjzHABhR5T1@ep-orange-tooth-aopm9nfy-pooler.c-2.ap-southeast-1.aws.neon.tech/neondb?sslmode=require'
});

async function check() {
  await client.connect();
  
  // Check location history count
  const res = await client.query('SELECT COUNT(*) FROM user_location_history');
  console.log('Location records count:', res.rows[0].count);

  // Check user list
  const users = await client.query('SELECT id, email FROM users');
  console.log('Users:', users.rows);

  for (const user of users.rows) {
      const locs = await client.query('SELECT * FROM user_location_history WHERE user_id = $1 ORDER BY recorded_at DESC LIMIT 1', [user.id]);
      console.log(`Last location for user ${user.email}:`, locs.rows.length > 0 ? locs.rows[0] : 'None');
  }

  await client.end();
}
check().catch(console.error);
