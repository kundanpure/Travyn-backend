import psycopg2
import sys

db_url = "postgres://travyn_owner:yv1Pj5qNExbB@ep-silent-fire-a5v594x7.us-east-2.aws.neon.tech/travyn?sslmode=require"
try:
    conn = psycopg2.connect(db_url)
    cursor = conn.cursor()
    cursor.execute("SELECT token, is_active FROM sos_tokens ORDER BY created_at DESC LIMIT 10")
    records = cursor.fetchall()
    print("Recent tokens:")
    for row in records:
        print(f"Token: {row[0]}, Active: {row[1]}")
    
    # Also specifically check for YCOB
    cursor.execute("SELECT token, is_active FROM sos_tokens WHERE token = 'YCOBkxaS8KnYA2SpKhyB6xvYo9PcWcMFSmSLjrhEI'")
    row = cursor.fetchone()
    if row:
        print(f"YCOB Token found! Active: {row[1]}")
    else:
        print("YCOB Token NOT FOUND in database!")

except Exception as e:
    print(f"Error: {e}")
finally:
    if 'conn' in locals():
        conn.close()
