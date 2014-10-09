;;; run with lein exec -p create_user.clj
(require '[cmr.oracle.user :as o]
         '[cmr.oracle.config :as oracle-config]
         '[cmr.metadata-db.config :as mdb-config])

(let [db (oracle-config/sys-dba-db-spec)
      metadata-db-user (mdb-config/db-username)]
  (o/drop-user db metadata-db-user))
