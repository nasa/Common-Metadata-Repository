(ns cmr.access-control.data.bulk-index
  "Performs bulk indexing of access control data."
  (:require
   [clj-time.core :as t]
   [cmr.access-control.data.elasticsearch :as es]
   [cmr.common.concepts :as cs]
   [cmr.common.date-time-parser :as date]
   [cmr.common.log :refer [info debug]]
   [cmr.common.time-keeper :as tk]))

(defn filter-expired-concepts
  "Remove concepts that have an expired delete-time."
  [batch]
  (filter (fn [concept]
            (let [delete-time-str (get-in concept [:extra-fields :delete-time])
                  delete-time (when delete-time-str
                                (date/parse-datetime delete-time-str))]
              (or (nil? delete-time)
                  (t/after? delete-time (tk/now)))))
          batch))

(defmulti prepare-batch
  "Returns the batch of concepts into elastic docs for bulk indexing."
  (fn [context batch options]
    (cs/concept-id->type (:concept-id (first batch)))))

(defmethod prepare-batch :default
  [context batch options]
  (es/prepare-batch context (filter-expired-concepts batch) options))

(defn bulk-index
  "Index many concepts at once using the elastic bulk api. The concepts to be indexed are passed
  directly to this function - it does not retrieve them from metadata db.
  The bulk API is invoked repeatedly if necessary -
  processing batch-size concepts each time. Returns the number of concepts that have been indexed.

  Valid options:
  * :force-version? - true indicates that we should overwrite whatever is in elasticsearch with the
  latest regardless of whether the version in the database is older than the _version in elastic."
  ([context concept-batches]
   (bulk-index context concept-batches nil))
  ([context concept-batches options]
   (reduce (fn [num-indexed batch]
             (let [batch (prepare-batch context batch options)]
               (es/bulk-index-documents context batch)
               (+ num-indexed (count batch))))
           0
           concept-batches)))
