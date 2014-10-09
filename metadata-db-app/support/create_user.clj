;;; run with lein exec -p create_user.clj
(require '[cmr.oracle.user :as o]
         '[cmr.oracle.config :as oracle-config]
         '[cmr.metadata-db.config :as mdb-config])

(let [db (oracle-config/sys-dba-db-spec)
      catalog-rest-user (mdb-config/catalog-rest-db-username)
      metadata-db-user (mdb-config/db-username)]
	(o/create-user db metadata-db-user (mdb-config/db-password))
	(o/grant-select-privileges db catalog-rest-user metadata-db-user))