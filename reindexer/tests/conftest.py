import os

# Unit tests never touch a real Oracle instance.  Set the backend to the
# in-memory stub before any app modules are imported so that app.db does not
# try to instantiate OracleClient (which would call oracledb.create_pool).
os.environ.setdefault("DB_BACKEND", "stub")
