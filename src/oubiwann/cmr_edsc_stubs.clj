(ns oubiwann.cmr-edsc-stubs
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [clojure.pprint :refer [pprint]]))

(def services-dir (io/resource "data/services"))
(def variables-dir (io/resource "data/variables"))

(defn get-files
  [dir]
  (->> dir
       (io/file)
       (file-seq)
       (filter #(.isFile %))))

(defn get-db
  []
  (get-in system [:apps :metadata-db :db :spec]))

(defn load-service
  [filename]
  (let [metadata (slurp filename)]
    (pprint metadata)
    (jdbc/with-db-connection [db-conn (get-db)]
      ;(jdbc/insert! db-conn :cmr_services {})
      )))

(defn load-variable
  [filename]
  (let [metadata (slurp filename)]
    (pprint metadata)
    (jdbc/with-db-connection [db-conn (get-db)]
      ;(jdbc/insert! db-conn :cmr_variables {})
      )))

(defn load-services
  []
  (->> services-dir
       (get-files)
       (map load-service)))

(defn load-variables
  []
  (->> variables-dir
       (get-files)
       (map load-variable)))

; (jdbc/with-db-connection [db-conn (get-db)]
;   (pprint (jdbc/query db-conn ["SELECT * FROM CMR_SERVICES"])))

; (jdbc/with-db-connection [db-conn (get-db)]
;   (pprint (jdbc/query db-conn ["SELECT * FROM CMR_VARIABLES"])))
