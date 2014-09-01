(ns cmr.search.models.results
  "Defines types for search results")

(defrecord Results
  [
   ;; The number of hits
   hits

   ;; The number of milliseconds the search took
   took

   ;; The result format requested by the user.
   result-format

   ;; Sequence of result items found by the query
   items

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Collection Results Only

   ;; A map of collection concept ids to granule counts.
   ;; This will only be present in the case of collection queries which enable this feature
   granule-counts-map

   ;; A map of collection concept ids to boolean values indicating if a collection has any granules at all
   has-granules-map

  ])

