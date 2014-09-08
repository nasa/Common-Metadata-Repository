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

(defn normalize-score
  "The score is divided by 2 to mimic the Catalog REST logic that tries to keep the boosts normalized
  between 0.0 and 1.0. That doesn't actually work but it at least matches Catalog REST's style. As
  of this writing there are plans to improve the relevancesort algorithm to better match client's
  expectations of better results. We will wait until that time to come up with a more reasonable
  approach."
  [score]
  (when score (/ score 2.0)))