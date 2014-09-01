(ns cmr.search.models.results
  "Defines types for search results")

;; Defines a single faceted field.
(defrecord Facet
  [
   ;; The field name. This will match the parameter field name accepted in searches
   field

   ;; A sequence of value count pairs. These are values that appear in the fields with counts of
   ;; the number of appearances of that value.
   value-counts
   ])


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

   facets

  ])

