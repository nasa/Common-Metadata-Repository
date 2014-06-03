;;; run with lein exec create_user.clj

(use '[leiningen.exec :only (deps)])
(deps '[[nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]]
      :repositories {"releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"})

(require '[cmr.oracle.user :as o]
         '[cmr.common.config :as config])

;; TODO change the password for different systems

(let [db (o/sys-dba-conn)
      tablespace ((config/config-value-fn :catalog-rest-tablespace "DEV_52_CATALOG_REST"))
      from-user ((config/config-value-fn :catalog-rest-user "DEV_52_CATALOG_REST"))
      to_user ((config/config-value-fn :metadata-db-user "METADATA_DB"))]
	(o/create-user db "METADATA_DB" "METADATA_DB")
	(o/grant-select-priviliges db tablespace from-user to_user))