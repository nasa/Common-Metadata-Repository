(ns cmr.opendap.ous.collection.results
  (:require
    [taoensso.timbre :as log]))

(defrecord CollectionResults
  [;; The number of results returned
   hits
   ;; Number of milleseconds elapsed from start to end of call
   took
   ;; The actual items in the result set
   items])

(defn create
  [results & {:keys [elapsed]}]
  (log/debug "Got results:" results)
  (if (:errors results)
    (assoc results :status 400)
    (map->CollectionResults
      {;; Our 'hits' is simplistic for now; will change when we support
       ;; paging, etc.
       :hits (count results)
       :took elapsed
       :items results})))
