(ns cmr.search.models.results
  "Defines types for search results")

;; A single catalog item reference.
(defrecord Reference
  [
   ;; CMR concept id, i.e. C5-PROV1
   concept-id

   revision-id

   provider-id

   name
   ])

(defrecord Results
  [
   ;; The number of hits
   hits

   ;; Sequence of references found in the query
   references
  ])