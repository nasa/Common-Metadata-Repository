;;; run with lein exec create_user.clj

(use '[leiningen.exec :only (deps)])
(deps '[[nasa-cmr/cmr-oracle-lib "0.1.0-SNAPSHOT"]]
      :repositories {"releases" "http://devrepo1.dev.echo.nasa.gov/data/dist/projects/echo/mavenrepo/"})

(require '[cmr.oracle.user :as o])

(o/drop-user (o/sys-dba-conn) "METADATA_DB")
