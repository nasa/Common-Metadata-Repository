(ns cmr.index-set.services.index-service
  "Provide functions to index concept"
  (:require [clojure.string :as s]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.index-set.data.elasticsearch :as es]
            [cmr.umm.echo10.collection :as collection]
            [cmr.system-trace.core :refer [deftracefn]]))

;; configured list of cmr concepts
(def cmr-concepts (list :collection :granule))

(defn- build-indices-list-w-config
  "given a index-set build list of indices with config"
  [idx-set]
  (flatten  (for [concept cmr-concepts id (list (-> idx-set
                                                    :index-set
                                                    :id))]
              (let [indices-config (-> idx-set
                                       :index-set
                                       concept)
                    index-names (:index-names indices-config)
                    settings (:settings indices-config)
                    mapping (:mapping indices-config)]
                (map #(hash-map :index-name (str id "_" %)
                                :settings  settings
                                :mapping mapping) index-names)))))

;; TODO - implement rollback (remove indices alleardy created in this index-set if index creation in elastic fails)
(deftracefn create-index-set
  "create indices listed in index-set"
  [context index-set]
  (let [indices (build-indices-list-w-config index-set)]
    (info indices)
    (map #(es/create-index %) indices)))

