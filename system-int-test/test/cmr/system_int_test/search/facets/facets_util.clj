(ns cmr.system-int-test.search.facets.facets-util
  "Helper vars and functions for testing collection facet responses."
  (:require [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.search.services.query-execution.facets.facets-v2-helper :as v2h]
            [cmr.common.util :as util]))

(defn make-coll
  "Helper for creating and ingesting an ECHO10 collection"
  [n prov & attribs]
  (d/ingest prov (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))))

(defn projects
  [& project-names]
  {:projects (apply dc/projects project-names)})

(def platform-short-names
  "List of platform short names that exist in the test KMS hierarchy. Note we are testing case
  insensivity of the short name. DIADEM-1D is the actual short-name value in KMS, but we expect
  diadem-1D to match."
  ["diadem-1D" "DMSP 5B/F3" "A340-600" "SMAP"])

(def instrument-short-names
  "List of instrument short names that exist in the test KMS hierarchy. Note we are testing case
  insensivity of the short name. LVIS is the actual short-name value in KMS, but we expect
  lVIs to match."
  ["ATM" "lVIs" "ADS" "SMAP L-BAND RADIOMETER"])

(def FROM_KMS
  "Constant indicating that the short name for the field should be a short name found in KMS."
  "FROM_KMS")

(defn platforms
  "Creates a specified number of platforms each with a certain number of instruments and sensors"
  ([prefix num-platforms]
   (platforms prefix num-platforms 0 0))
  ([prefix num-platforms num-instruments]
   (platforms prefix num-platforms num-instruments 0))
  ([prefix num-platforms num-instruments num-sensors]
   {:platforms
    (for [pn (range 0 num-platforms)
          :let [platform-name (str prefix "-p" pn)]]
      (dc/platform
        {:short-name (if (= FROM_KMS prefix)
                       (or (get platform-short-names pn) platform-name)
                       platform-name)
         :long-name platform-name
         :instruments
         (for [in (range 0 num-instruments)
               :let [instrument-name (str platform-name "-i" in)]]
           (dc/instrument
             {:short-name (if (= FROM_KMS prefix)
                            (or (get instrument-short-names in) instrument-name)
                            instrument-name)
              :sensors (for [sn (range 0 num-sensors)
                             :let [sensor-name (str instrument-name "-s" sn)]]
                         (dc/sensor {:short-name sensor-name}))}))}))}))

(defn twod-coords
  [& names]
  {:two-d-coordinate-systems (map dc/two-d names)})

(defn science-keywords
  [& sks]
  {:science-keywords sks})

(defn processing-level-id
  [id]
  {:processing-level-id id})

(defn generate-science-keywords
  "Generate science keywords based on a unique number."
  [n]
  (dc/science-keyword {:category (str "Cat-" n)
                       :topic (str "Topic-" n)
                       :term (str "Term-" n)
                       :variable-level-1 "Level1-1"
                       :variable-level-2 "Level1-2"
                       :variable-level-3 "Level1-3"
                       :detailed-variable (str "Detail-" n)}))

(defn prune-facet-response
  "Recursively limit the facet response to only the keys provided to make it easier to test
  different parts of the response in different tests."
  [facet-response keys]
  (if (:children facet-response)
    (assoc (select-keys facet-response keys)
           :children
           (for [child-facet (:children facet-response)]
             (prune-facet-response child-facet keys)))
    (select-keys facet-response keys)))

(defn facet-group
  "Returns the facet group for the given field."
  [facet-response field]
  (let [child-facets (:children facet-response)
        field-title (v2h/fields->human-readable-label field)]
    (first (filter #(= (:title %) field-title) child-facets))))

(defn applied?
  "Returns whether the provided facet field is marked as applied in the facet response."
  [facet-response field]
  (:applied (facet-group facet-response field)))

(defn facet-values
  "Returns the values of the facet for the given field"
  [facet-response field]
  (map :title (:children (facet-group facet-response field))))

(defn facet-index
  "Returns the (first) index of the facet value in the list"
  [facet-response field value]
  (first (keep-indexed #(when (= value %2) %1)
                       (facet-values facet-response field))))

(defn facet-included?
  "Returns whether the provided facet value is included in the list"
  [facet-response field value]
  (some #(= value %) (facet-values facet-response field)))

(defn in-alphabetical-order?
  "Returns true if the values in the collection are sorted in alphabetical order."
  [coll]
  ;; Note that compare-natural-strings is thoroughly unit tested so we can use it to verify
  ;; alphabetical order
  (= coll (sort-by first util/compare-natural-strings coll)))
