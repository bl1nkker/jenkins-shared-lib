#!/usr/bin/env python3

import os
import sys
import logging
import argparse
from datetime import datetime, timedelta, timezone

import psycopg2


def get_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Environment variable {name} must be set")
    return value


def parse_args():
    parser = argparse.ArgumentParser(
        description="Cleanup old records from mq.queue_message"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true"
    )
    return parser.parse_args()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
    args = parse_args()

    cutoff = datetime.now(timezone.utc) - timedelta(days=30)

    logging.info("DRY_RUN: %s", args.dry_run)
    logging.info("Cutoff datetime (UTC): %s", cutoff.isoformat())

    psql_host = get_env("PSQL_MQ_URL")
    psql_user = get_env("PSQL_MQ_USER")
    psql_password = get_env("PSQL_MQ_PASSWORD")

    logging.info("Connecting to PostgreSQL host: %s", psql_host)
    try:
        conn = psycopg2.connect(
            host=psql_host,
            user=psql_user,
            password=psql_password,
        )
    except Exception:
        raise Exception("Failed to connect to PostgreSQL")
    try:
        with conn:
            with conn.cursor() as cur:
                if args.dry_run:
                    cur.execute(
                        """
                        SELECT COUNT(*) AS cnt
                        FROM mq.queue_message
                        WHERE creation_date <= %s
                        """,
                        (cutoff,),
                    )
                    count = cur.fetchone()["cnt"]
                    logging.info("%s records would be deleted from mq.queue_message", count)
                else:
                    cur.execute(
                        """
                        DELETE FROM mq.queue_message
                        WHERE creation_date <= %s
                        """,
                        (cutoff,),
                    )
                    deleted = cur.rowcount
                    logging.info("Deleted %s records from mq.queue_message", deleted)
    except Exception:
        raise Exception("Cleanup job failed, transaction will be rolled back")
    finally:
        conn.close()
        logging.info("Database connection closed")

    logging.info("Cleanup job finished successfully")

