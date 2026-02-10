import psycopg2
import os
from datetime import datetime, timedelta

conn = psycopg2.connect(
    host=os.environ.get("PSQL_MQ_URL"),
    user=os.environ.get("PSQL_MQ_USER"),
    password=os.environ.get("PSQL_MQ_PASSWORD")
)

cutoff = datetime.utcnow() - timedelta(days=30)

with conn:
    with conn.cursor() as cur:
        cur.execute("""
            DELETE FROM mq.queue_message
            WHERE creation_date <= %s
            RETURNING id
        """, (cutoff,))
        deleted = cur.rowcount
        print(f"Deleted: {deleted}")
        #cur.execute("""
        #    SELECT * FROM mq.queue_message
        #    WHERE creation_date >= %s
        #""", (cutoff,))

