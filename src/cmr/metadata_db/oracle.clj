(ns cmr.metadata-db.oracle)

(def db-atom (atom nil))

(defn get-db
  "Returns the db-atom that is populated with db instance"
  []
  @db-atom)

(defn set-db!
  "Sets the db-atom to the given db instance"
  [db]
  (reset! db-atom db))