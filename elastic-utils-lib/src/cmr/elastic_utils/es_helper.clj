(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [cmr.common-app.config :as common-config]
   [clojurewerkz.elastisch.rest.document :as doc]
   [clojurewerkz.elastisch.rest.index :as esi]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.connect :as esc]))

(defmulti search
  "Performs a search query across one or more indexes and one or more mapping types
   based on search-es-engine type."
  (fn [conn index mapping-type & args]
    (common-config/search-es-engine-key)))

(defmethod search :old
  [conn index mapping-type & args]
  (apply doc/search conn index mapping-type args))

(defmethod search :new
  [conn index mapping-type & args]
  ;; TODO: add implementation
  )

(defmulti count-query
  "Performs a count query over one or more indexes and types
   based on search-es-engine type."
  (fn [conn index mapping-type query]
    (common-config/search-es-engine-key)))

(defmethod count-query :old
  [conn index mapping-type query]
  (doc/count conn index mapping-type query))

(defmethod count-query :new
  [conn index mapping-type query]
  ;; TODO: add implementation
  )

(defmulti scroll
  "Performs a count query over one or more indexes and types
   based on search-es-engine type."
  (fn [conn scroll-id opts]
    (common-config/search-es-engine-key)))

(defmethod scroll :old
  [conn scroll-id opts]
  (doc/scroll conn scroll-id opts))

(defmethod scroll :new
  [conn scroll-id opts]
  ;; TODO: add implementation
  )
