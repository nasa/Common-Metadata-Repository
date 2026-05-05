import logging
import os
import sys

import oracledb

logger = logging.getLogger(__name__)

def connect_to_db():
    """
    Establishes the connection to the DB and returns the connection.

    Args:
        None
    
    Returns:
        The database connection

    Exceptions:
        Logs error and exits on issue connecting to DB
    """
    db_username = os.getenv("DB_USERNAME")
    db_password = os.getenv("DB_PASSWORD")
    db_dsn = os.getenv("DB_URL")

    try:
        db_connection = oracledb.connect(user=db_username, password=db_password, dsn=db_dsn)
    except oracledb.Error as e:
        logger.error(f"ERROR: failed to connect to DB: {e}")
        sys.exit(1)
    return db_connection
