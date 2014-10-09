;;; run with lein exec -p create_user.clj
(require '[cmr.oracle.user :as o]
         '[cmr.oracle.config :as oracle-config]
         '[cmr.ingest.config :as ingest-config])

(let [db (oracle-config/sys-dba-db-spec)]
	(o/create-user db (ingest-config/db-username) (ingest-config/db-password)))