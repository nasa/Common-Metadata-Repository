(ns cmr.search.models.results
  "Defines types for search results")

;; A single catalog item reference.
(defrecord Reference
  [
   ;; CMR concept id, i.e. C5-PROV1
   concept-id

   revision-id

   location

   name
   ])

(defrecord Results
  [
   ;; The number of hits
   hits

   ;; The number of milliseconds the search took
   took

   ;; Sequence of references found in the query
   references
  ])