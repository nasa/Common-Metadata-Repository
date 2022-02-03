(ns cmr.common-app.services.search.results-model
  "Defines types for search results"
  (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

;; Defines a single faceted field.
(defrecord Facet
  [
   ;; The field name. This will match the parameter field name accepted in searches
   field

   ;; A sequence of value count pairs. These are values that appear in the fields with counts of
   ;; the number of appearances of that value.
   value-counts])


(defrecord Results
  [
   ;; The number of hits
   hits

   ;; The session-id to be used for subsequent scroll requests
   scroll-id
   
   ;; The search-after value for search-after requests
   search-after

   ;; The number of milliseconds the search took
   took

   ;; Whether or not the Elasticsearch query timed out
   timed-out

   ;; The result format requested by the user.
   result-format

   ;; Sequence of result items found by the query
   items

   facets])

(record-pretty-printer/enable-record-pretty-printing
  Facet
  Results)
