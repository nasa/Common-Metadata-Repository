(ns cmr.access-control.data.bulk-index
  "Performs bulk indexing of access control data."
  (:require
   [clj-time.format :as f]
   [cmr.access-control.data.elasticsearch :as es]
   [cmr.common.concepts :as cs]
   [cmr.common.log :refer [info]]
   [cmr.common.util :as util]))

(defmulti prepare-batch
  "Returns the batch of concepts into elastic docs for bulk indexing."
  (fn [context batch options]
    (cs/concept-id->type (:concept-id (first batch)))))

(defmethod prepare-batch :default
  [context batch options]
  (es/prepare-batch context batch options))

(defn- get-max-revision-date
  "Takes a batch of concepts to index and returns the maximum revision date."
  [batch previous-max-revision-date]
  (->> batch
       ;; Get the revision date of each item
       (map :revision-date)
       ;; Parse the date
       (map #(f/parse (f/formatters :date-time) %))
       ;; Add on the last date
       (cons previous-max-revision-date)
       ;; Remove nil because previous-max-revision-date could be nil
       (remove nil?)
       (apply util/max-compare)))

(defn bulk-index-with-revision-date
  "Index many concepts at once using the elastic bulk api. The concepts to be indexed are passed
  directly to this function - it does not retrieve them from metadata db (tag associations for
  collections WILL be retrieved, however). The bulk API is invoked repeatedly if necessary -
  processing batch-size concepts each time. Returns the number of concepts that have been indexed.

  Valid options:
  * :all-revisions-index? - true indicates this should be indexed into the all revisions index
  * :force-version? - true indicates that we should overwrite whatever is in elasticsearch with the
  latest regardless of whether the version in the database is older than the _version in elastic.
  Returns a map with keys of :num-indexed and :max-revision-date."
  ([context concept-batches]
   (bulk-index-with-revision-date context concept-batches nil))
  ([context concept-batches options]
   (reduce (fn [{:keys [num-indexed max-revision-date]} batch]
             (let [max-revision-date (get-max-revision-date batch max-revision-date)
                   batch (prepare-batch context batch options)]
               (es/bulk-index-documents context batch)
               {:num-indexed (+ num-indexed (count batch))
                :max-revision-date max-revision-date}))
           {:num-indexed 0 :max-revision-date nil}
           concept-batches)))
