(ns cmr.search.models.results
  "Defines types for search results")



(defrecord Results
  [
   ;; The number of hits
   hits

   ;; The number of milliseconds the search took
   took


   ;; TODO change this to items
   ;; Sequence of references found in the query
   references

   ;; TODO add result format

  ])


