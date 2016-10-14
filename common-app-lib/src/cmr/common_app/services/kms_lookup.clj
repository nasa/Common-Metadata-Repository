(ns cmr.common-app.services.kms-lookup
  "Functions to support fast lookup of KMS keywords. We store
  a map to lookup by leaf node ID such as short-name, a map to look up a map by its hash, and a map
  to look up the full hierarchy for any location keyword string."
  (:require
   [cmr.common.util :as util]
   [clojure.string :as str]
   [cmr.transmit.kms :as kms]))

;; TODO - what about providers - potentially only lookup using short-name?
(def kms-scheme->fields-for-umm-c-lookup
  "Maps the KMS keyword scheme to the list of fields that should be matched when comparing fields
  between UMM-C and KMS."
  {:science-keywords [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3]
   :platforms [:short-name :long-name]
   :instruments [:short-name :long-name]
   :projects [:short-name :long-name]
   :spatial-keywords [:category :type :subregion-1 :subregion-2 :subregion-3]})

(defn- normalize-for-lookup
  "Takes a map (either a UMM-C keyword or a KMS keyword) and a list of fields from the map which we
  want to use for comparison. We return a map containing only the keys we are interested in and with
  all values in lower case."
  [m fields-to-compare]
  (->> (select-keys m fields-to-compare)
       util/remove-nil-keys
       (util/map-values str/lower-case)))

;; TODO rewrite
(defn- generate-lookup-by-umm-c-map
  "Takes a GCMD keywords map and stores them in a way for faster lookup when trying to find
  a location keyword that matches a UMM-C collection with a location keyword in KMS. For each KMS
  keyword there are a set of fields which are used to match against the same fields in UMM-C. We
  store the GCMD keywords in a map with a hash of the map as the key to that map for fast lookup."
  [gcmd-keywords-map]
  (into {}
        (map (fn [[keyword-scheme keyword-maps]]
               [keyword-scheme (let [fields (get kms-scheme->fields-for-umm-c-lookup
                                                 keyword-scheme)]
                                 (into {}
                                       (map (fn [keyword-map]
                                              [(normalize-for-lookup keyword-map fields)
                                               keyword-map])
                                            keyword-maps)))])
             gcmd-keywords-map)))

;; TODO - what about projects?
(def keywords-to-lookup-by-short-name
  "Set of KMS keywords that we need to be able to lookup by short name."
  #{:providers :platforms :instruments})

(defn generate-lookup-by-short-name-map
  "Create a map with the leaf node identifier in all lower case as keys to the full hierarchy
   for that entry. GCMD ensures that no two leaf fields can be the same when compared in a case
   insensitive manner."
  [gcmd-keywords-map]
  (into {}
        (map (fn [[keyword-scheme keyword-maps]]
               (let [maps-by-short-name (into {}
                                              (for [entry keyword-maps]
                                                [(str/lower-case (:short-name entry)) entry]))]
                 [keyword-scheme maps-by-short-name]))
             (select-keys gcmd-keywords-map keywords-to-lookup-by-short-name))))

(def duplicate-keywords
  "Lookup table to account for any duplicate keywords. Will choose the preferred value.
  Common key is :uuid which is a field in the location-keyword map. "
   ;; Choose Black Sea here because it's more associated with Eastern Europe than Western Asia.
  {"BLACK SEA" {:category "CONTINENT" :type "EUROPE" :subregion-1 "EASTERN EUROPE"
                :subregion-2 "BLACK SEA" :uuid "afbc0a01-742e-49da-939e-3eaa3cf431b0"}
   ;; Choose a more specific SPACE element because the general SPACE is too broad and top-level.
   "SPACE" {:category "SPACE" :type "EARTH MAGNETIC FIELD" :subregion-1 "SPACE"
            :uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}
   ;; Choose Georgia the country instead of Georgia the US State.
   "GEORGIA" {:category "CONTINENT" :type "ASIA" :subregion-1 "WESTERN ASIA" :subregion-2 "GEORGIA"
              :uuid "d79e134c-a4d0-44f2-9706-cad2b59de992"}})

(defn- generate-lookup-by-location-map
  "Create a map every location string as keys to the full hierarchy for that entry. If there are
  multiple strings, the one with the fewest hierarchical keys is chosen. For example 'OCEAN' will
  map to the keyword {:category \"OCEAN\"} rather than {:category \"OCEAN\" :type \"ARCTIC OCEAN\"}."
  [gcmd-keywords-map]
  (let [location-keywords (->> gcmd-keywords-map :spatial-keywords (sort-by count) reverse)
        location-keywords (into {}
                            (for [location-keyword-map location-keywords
                                  location (vals (dissoc location-keyword-map :uuid))]
                              [(str/upper-case location) location-keyword-map]))]
    (merge location-keywords duplicate-keywords)))

(defn create-kms-index
  "Creates the KMS index structure to be used for fast lookups."
  [kms-keywords-map]
  (let [short-name-lookup-map (generate-lookup-by-short-name-map kms-keywords-map)
        umm-c-lookup-map (generate-lookup-by-umm-c-map kms-keywords-map)
        location-lookup-map (generate-lookup-by-location-map kms-keywords-map)]
    (merge kms-keywords-map
           {:short-name-index short-name-lookup-map
            :umm-c-index umm-c-lookup-map
            :locations-index location-lookup-map})))

(defn deflate
  "Takes a KMS index and returns a minimal version to store more efficiently and in a way that
  the index can be recreated in a way to reduce the memory usage."
  [kms-index]
  (dissoc kms-index :short-name-index :umm-c-index :locations-index))

(defn lookup-by-short-name
  "Takes a kms-index, the keyword scheme, and a short name and returns the full KMS hierarchy for
  that short name. Comparison is made case insensitively."
  [kms-index keyword-scheme short-name]
  (get-in kms-index [:short-name-index keyword-scheme (str/lower-case short-name)]))

(defn lookup-by-location-string
  "Takes a kms-index and a location string and returns the full KMS hierarchy for that location
  string. Comparison is made case insensitively."
  [kms-index location-string]
  (get-in kms-index [:locations-index (str/upper-case location-string)]))

(defn lookup-by-umm-c-keyword
  "Takes a keyword as represented in UMM-C and returns the KMS keyword. Comparison is made case
  insensitively."
  [kms-index keyword-scheme umm-c-keyword]
  (let [comparison-map (normalize-for-lookup umm-c-keyword (kms-scheme->fields-for-umm-c-lookup
                                                            keyword-scheme))]
    (get-in kms-index [:umm-c-index keyword-scheme comparison-map])))
