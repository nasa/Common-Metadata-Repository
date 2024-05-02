(ns cmr.elastic-utils.search.es-wrapper
  "Wraps common elastic functions for use outside of elastic-utils so that other
   namespaces do not need to import anything from clojurewerkz."
  (:require
   [clojurewerkz.elastisch.query :as query]))

(defn match-all
  "See clojurewerkz for details"
  ([] (query/match-all))
  ([opts] (query/match-all opts)))
