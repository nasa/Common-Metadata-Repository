(ns cmr.elastic-utils.search.es-wrapper
  "Wraps common elastic functions for use outside of elastic-utils.")

(defn match-all
  "Returns a match-all query"
  ([] {:match_all {}})
  ([opts] {:match_all opts}))
