import psycopg2
import os
import logging
from datetime import datetime, timedelta

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    PSQL_MQ_URL = os.environ.get("PSQL_MQ_URL")

    if not PSQL_MQ_URL:
        raise ValueError("PSQL_MQ_URL must be set for this job")
    logging.info("PSQL_MQ_URL variable is set to '%s'", PSQL_MQ_URL)
    cutoff = datetime.utcnow() - timedelta(days=30)
    try:
        conn = psycopg2.connect(
            host=os.environ.get("PSQL_MQ_URL"),
            user=os.environ.get("PSQL_MQ_USER"),
            password=os.environ.get("PSQL_MQ_PASSWORD")
        )
    except:
        logging.error("Unable to connect to the database")
        return
    with conn:
        with conn.cursor() as cur:
            cur.execute("""
                DELETE FROM mq.queue_message
                WHERE creation_date <= %s
                RETURNING id
            """, (cutoff,))
            deleted = cur.rowcount
            logging.info("Deleted %s records", deleted)

