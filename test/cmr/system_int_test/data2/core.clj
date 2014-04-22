(ns cmr.system-int-test.data2.core
  "Contains helper functions for data generation and ingest for example based testing in system
  integration tests."
  (:require [cmr.umm.echo10.collection :as umm-c]
            [cmr.umm.echo10.granule :as umm-g]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-http.client :as client]
            [cheshire.core :as json])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))

(defmulti ingest-path
  "Returns the path to ingest the item"
  (fn [provider-id item]
    (type item)))

(defmethod ingest-path UmmCollection
  [provider-id item]
  (url/ingest-url provider-id :collection (:entry-title item)))

(defmethod ingest-path UmmGranule
  [provider-id item]
  (url/ingest-url provider-id :granule (:granule-ur item)))

(defn ingest
  "Ingests the catalog item. Returns it with concept-id and revision-id set on it."
  [provider-id item]
  (let [ingest-url (ingest-path provider-id item)
        xml (echo10/umm->echo10-xml item)
        response (client/put ingest-url
                             {:content-type :echo10+xml
                              :body xml})]
    (merge item (json/decode (:body response) true))))


(defmacro record-fields
  "Returns the set of fields in a record type as keywords. The record type passed in must be a java
  class. Uses the getBasis function on record classes which returns a list of symbols of the fields of
  the record."
  [record-type]
  `(map keyword  ( ~(symbol (str record-type "/getBasis")))))

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