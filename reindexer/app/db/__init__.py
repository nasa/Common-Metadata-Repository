"""Select the DB backend based on DB_BACKEND env var.

DB_BACKEND=oracle  (default) → OracleClient (direct Oracle via oracledb)
DB_BACKEND=stub              → StubOracleClient (hardcoded fake data, for unit tests)
"""
from app.config import config

if config.db_backend == "stub":
    from app.db.stub import db_client
else:
    from app.db.oracle import OracleClient
    db_client = OracleClient()
