(ns cmr.system-int-test.data2.core
  "Contains helper functions for data generation and ingest for example based testing in system
  integration tests."
  (:require [clojure.test :refer [is]]
            [cmr.umm.core :as umm]
            [cmr.common.mime-types :as mime-types]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))

(defmulti item->native-id
  "Returns the native id of an item"
  (fn [item]
    (type item)))

(defmethod item->native-id UmmCollection
  [item]
  (:entry-title item))

(defmethod item->native-id UmmGranule
  [item]
  (:granule-ur item))

(defmulti item->concept-type
  "Returns the path to ingest the item"
  (fn [item]
    (type item)))

(defmethod item->concept-type UmmCollection
  [item]
  :collection)

(defmethod item->concept-type UmmGranule
  [item]
  :granule)

(defn item->concept
  "Converts an UMM item to a concept map. Expects provider-id to be in the item"
  [item format-key]
  (let [format (mime-types/format->mime-type format-key)]
    (merge {:concept-type (item->concept-type item)
            :provider-id (:provider-id item)
            :native-id (item->native-id item)
            :metadata (umm/umm->xml item format-key)
            :format format}
           (when (:concept-id item)
             {:concept-id (:concept-id item)})
           (when (:revision-id item)
             {:revision-id (:revision-id item)}))))

(defn ingest
  "Ingests the catalog item. Returns it with concept-id, revision-id, and provider-id set on it."
  ([provider-id item]
   (ingest provider-id item :echo10))
  ([provider-id item format-key]
   (let [response (ingest/ingest-concept
                    (item->concept (assoc item :provider-id provider-id) format-key))]
     (is (= 200 (:status response))
         (pr-str response))
     (assoc item
            :provider-id provider-id
            :concept-id (:concept-id response)
            :revision-id (:revision-id response)))))

(defn item->ref
  "Converts an item into the expected reference"
  [item]
  (let [{:keys [concept-id revision-id]} item]
    {:name (item->native-id item)
     :id concept-id
     :location (str (url/location-root) (:concept-id item))
     :revision-id revision-id}))

(defn item->metadata-result
  "Converts an item into the expected metadata result"
  [format-key item]
  (let [{:keys [concept-id revision-id collection-concept-id]} item]
    {:concept-id concept-id
     :revision-id revision-id
     :format format-key
     :collection-concept-id collection-concept-id
     :metadata (umm/umm->xml item format-key)}))

(defn metadata-results-match?
  "Returns true if the metadata results match the expected items"
  [format-key items search-result]
  (= (set (map (partial item->metadata-result format-key) items))
     (set search-result)))

(defn assert-metadata-results-match
  "Returns true if the metadata results match the expected items"
  [format-key items search-result]
  (is (= (set (map (partial item->metadata-result format-key) items))
         (set search-result))))

(defn refs-match?
  "Returns true if the references match the expected items"
  [items search-result]
  (let [result (= (set (map item->ref items))
                  ;; need to remove score etc. because it won't be available in collections
                  ;; to which we are comparing
                  (set (map #(dissoc % :score :granule-count) (:refs search-result))))]
    (when (:status search-result)
      (println (pr-str search-result)))
    result))

(defn refs-match-order?
  "Returns true if the references match the expected items in the order specified"
  [items search-result]
  (let [result (= (map item->ref items)
                  (map #(dissoc % :score :granule-count) (:refs search-result)))]
    (when (:status search-result)
      (println (pr-str search-result)))
    result))

(def unique-num-atom
  (atom 0))

(defn reset-uniques
  "Resets the unique num value"
  []
  (reset! unique-num-atom 0))

(defn unique-num
  "Creates a unique number and returns it"
  []
  (swap! unique-num-atom inc))

(defn unique-str
  "Creates a unique string and returns it"
  ([]
   (unique-str "string"))
  ([prefix]
   (str prefix (unique-num))))

(defn make-datetime
  "Creates a datetime from a number added onto a base datetime"
  ([n]
   (make-datetime n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 2012 1 1 0 0 0)
                        (t/days n)
                        (t/hours n))]
       (if to-string?
         (f/unparse (f/formatters :date-time) date)
         date)))))

(defn make-time
  "Creates a time from a number added onto a base datetime"
  ([n]
   (make-time n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 1970 1 1 0 0 0)
                        (t/minutes n)
                        (t/seconds n))]
       (if to-string?
         (f/unparse (f/formatters :hour-minute-second) date)
         date)))))

(defn make-date
  "Creates a date from a number added onto a base datetime"
  ([n]
   (make-date n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 2012 1 1 0 0 0)
                        (t/days n))]
       (if to-string?
         (f/unparse (f/formatters :date) date)
         date)))))