import logging
import os
import sys

import oracledb
from parameters import CONFIG

logger = logging.getLogger("find_db")

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
    # 1. Use passed config if provided (Method for Program A)
    # 2. Else use global CONFIG if it has data (Method for Program B)
    # 3. Else fallback to os.environ
    settings = CONFIG or os.environ

    db_username = settings.get("DB_USERNAME")
    db_password = settings.get("DB_PASSWORD")
    db_dsn = settings.get("DB_URL")

    try:
        db_connection = oracledb.connect(user=db_username, password=db_password, dsn=db_dsn)
    except oracledb.Error as e:
        logger.error(f"ERROR: failed to connect to DB: {e}")
        sys.exit(1)
    return db_connection
