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

  ])


