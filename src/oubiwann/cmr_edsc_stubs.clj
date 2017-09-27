(ns oubiwann.cmr-edsc-stubs
  (:require
   [clojure.pprint :refer [pprint]]
   [oubiwann.cmr-edsc-stubs.data.core :as data]
   [oubiwann.cmr-edsc-stubs.data.sources :as data-sources]))

(defn load-service
  [filename]
  (let [metadata (slurp filename)]
    (pprint metadata)
    ;(data/insert system :cmr_services {})
    ))

(defn load-variable
  [filename]
  (let [metadata (slurp filename)]
    (pprint metadata)
    ;(data/insert system :cmr_variables {})
    ))

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

; (data/query system "SELECT * FROM CMR_VARIABLES")
