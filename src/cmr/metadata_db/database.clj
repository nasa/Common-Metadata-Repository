(ns cmr.metadata-db.database
  "Defines a protocol for basic operations on a database.")

(defprotocol Database
  "Provides functions for basic database operations."
  
  (reset
    [db]
    "Delete everything from the database.  USE WITH CAUTION."))