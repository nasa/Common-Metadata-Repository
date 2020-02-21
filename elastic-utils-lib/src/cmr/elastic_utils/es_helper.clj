(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [clojurewerkz.elastisch.rest.document :as doc]))

(defn search
  "Performs a search query across one or more indexes and one or more mapping types"
  [conn index mapping-type opts]
  (doc/search conn index mapping-type opts))

(defn count-query
  "Performs a count query over one or more indexes and types"
  [conn index mapping-type query]
  (doc/count conn index mapping-type query))

(defn scroll
  "Performs a count query over one or more indexes and types"
  [conn scroll-id opts]
  (doc/scroll conn scroll-id opts))
