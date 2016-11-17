(ns cmr.access-control.data.bulk-index
  "Performs bulk indexing of access control data."
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [cmr.access-control.data.elasticsearch :as es]
   [cmr.common.concepts :as cs]
   [cmr.common.date-time-parser :as date]
   [cmr.common.log :refer [info debug]]
   [cmr.common.time-keeper :as tk]
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
  "See documentation for bulk-index. This is a temporary function added for supporting replication
  using DMS. It does the same work as bulk-index, but instead of returning the number of concepts
  indexed it returns a map with keys of :num-indexed and :max-revision-date."
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
