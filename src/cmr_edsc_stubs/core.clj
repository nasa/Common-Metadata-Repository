(ns cmr-edsc-stubs.core
  (:require
   [clojure.pprint :refer [pprint]]
   [cmr-edsc-stubs.data.core :as data]
   [cmr-edsc-stubs.data.sources :as data-sources]
   [cmr-edsc-stubs.util :as util]))

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
  (->> data-sources/services-dir
       (util/get-files)
       (map load-service)))

(defn load-variables
  []
  (->> data-sources/variables-dir
       (util/get-files)
       (map load-variable)))

; (data/query system "SELECT * FROM CMR_VARIABLES")
