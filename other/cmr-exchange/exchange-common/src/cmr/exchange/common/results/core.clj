(ns cmr.exchange.common.results.core
  (:require
    [clojusc.results.core :as core]))

(defrecord CollectionResults
  [;; The number of results returned
   hits
   ;; Number of milleseconds elapsed from start to end of call
   took
   ;; Search-after header
   search-after
   ;; The actual items in the result set
   items
   ;; The randomly generated request-id string
   request-id
   ;; Any non-error messages that need to be returned
   warnings])

(defn create
  [results & {:keys [request-id elapsed sa-header hits-header warnings]}]
  (map->CollectionResults
    (merge {
            :hits hits-header 
            :took elapsed
            :search-after sa-header
            :request-id request-id 
            :items results}
           warnings)))

(def elided #'core/elided)
(def remaining-items #'core/remaining-items)
