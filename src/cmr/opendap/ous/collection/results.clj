(ns cmr.opendap.ous.collection.results)

(defrecord CollectionResults
  [;; The number of results returned
   hits
   ;; Number of milleseconds elapsed from start to end of call
   took
   ;; The actual items in the result set
   items])

(defn create
  [results & {:keys [elapsed]}]
  (map->CollectionResults
    {;; Our 'hits' is simplistic for now; will change when we support
     ;; paging, etc.
     :hits (count results)
     :took elapsed
     :items results}))

(defn elided
  [results]
  (assoc results :items [(first (:items results) )"..."]))

(defn remaining-items
  [results]
  (rest (:items results)))
