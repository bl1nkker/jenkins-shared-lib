import psycopg2
import os
import logging
from datetime import datetime, timedelta

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    PSQL_URL = os.environ.get("PSQL_MQ_URL")

    if not PSQL_URL:
        raise ValueError("PSQL_MQ_URL must be set for this job")
    logging.info("PSQL_MQ_URL variable is set to '%s'", PSQL_MQ_URL)
    cutoff = datetime.utcnow() - timedelta(days=30)
    PSQL_USER = os.environ.get("PSQL_MQ_USER")
    PSQL_PASS = os.environ.get("PSQL_MQ_PASSWORD") 
    try:
        conn = psycopg2.connect(
            host=PSQL_URL,
            user=PSQL_USER,
            password=PSQL_PASS
        )
    except:
        raise ValueError("Unable to connect to the database")
    with conn:
        with conn.cursor() as cur:
            cur.execute("""
                DELETE FROM mq.queue_message
                WHERE creation_date <= %s
                RETURNING id
            """, (cutoff,))
            deleted = cur.rowcount
            logging.info("Deleted %s records", deleted)

