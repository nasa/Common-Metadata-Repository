(ns cmr.common-app.services.kms-lookup
  "Functions to support fast lookup of KMS keywords. The kms-index structure is a map with keys for
  each of the different KMS keywords In addition the kms-index has 3 additional 'index' keys to
  support fast retrieval. For example:
  {:providers [{:level-0 \"ACADEMIC\" :uuid \"abc\" ...}]
   :science-keywords [...]
   :platforms [...]
   ...
   :short-name-index {:platforms {\"TERRA\" {:category \"SATELLITES\" :short-name \"TERRA\" :uuid \"abc\"...}
								                ...}
                      :instruments {\"ATM\" {...}}}
   :umm-c-index {:spatial-keywords {{:category \"CONTINENT\" :subregion1 \"WESTERN AFRICA\"} ;; key
                                    {:category \"CONTINENT\" :subregion1 \"WESTERN AFRICA\" :uuid \"123\"} ;; value
                                   ...}
                 :science-keywords ...}
  :locations-index {\"WESTERN AFRICA\" {:category \"CONTINENT\" :type \"AFRICA\"
                                        :subregion-1 \"WESTERN AFRICA\" :uuid \"123\"}
                    \"CHAD\" {:category \"CONTINENT\" :type \"AFRICA\" :subregion-1 \"WESTERN AFRICA\"
                              :subregion-2 \"CHAD\" :uuid \"456\"}}}"
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as csk-extras]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.util :as util]))

(def kms-scheme->fields-for-umm-c-lookup
  "Maps the KMS keyword scheme to the list of fields that should be matched when
  comparing fields between KMS and UMM-C, UMM-G, UMM-S, UMM-T, or UMM-Var."
  {:science-keywords [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3]
   :platforms [:category :short-name :long-name] ;; :basis and :sub-category are not in metadata
   :instruments [:short-name :long-name]
   :projects [:short-name :long-name]
   :providers [:short-name]
   :spatial-keywords [:category :type :subregion-1 :subregion-2 :subregion-3]
   :concepts [:short-name]
   :iso-topic-categories [:iso-topic-category]
   :granule-data-format [:format]
   :mime-type [:mime-type]
   :related-urls [:url-content-type :type :subtype]})

(def kms-scheme->fields-for-umm-var-lookup
  "Maps the KMS keyword scheme to the list of fields that should be matched when comparing fields
  between UMM-Var and KMS."
  {:measurement-name [:context-medium :object :quantity]})

(defn- normalize-for-lookup
  "Takes a map (either a UMM-C keyword or a KMS keyword) or string m,
  and a list of fields from the map which we want to use for comparison.
  When m is a map we return a map containing only the keys we are interested
  in and with all values in lower case. When m is not a map, takes the first
  field from fields-to-compare as key and returns map of the form:
  {
    field-to-compare m
  }"
  [m fields-to-compare]
  (if (map? m)
    (->> (select-keys m fields-to-compare)
         util/remove-nil-keys
         (util/map-values str/lower-case))
    {(first fields-to-compare) (str/lower-case m)}))

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

(defn generate-lookup-by-measurement-name
  "Create a map with the measurement field values defined in UMM-Var map to the KMS keywords."
  [gcmd-keywords-map]
  (into {}
        (let [keyword-scheme :measurement-name
              keyword-maps (:measurement-name gcmd-keywords-map)]
          [[keyword-scheme (let [fields (get kms-scheme->fields-for-umm-var-lookup
                                             keyword-scheme)]
                             (into {}
                                   (map (fn [keyword-map]
                                          [(normalize-for-lookup keyword-map fields)
                                           keyword-map])
                                        keyword-maps)))]])))

(defn create-kms-index
  "Creates the KMS index structure to be used for fast lookups."
  [kms-keywords-map]
  (let [short-name-lookup-map (generate-lookup-by-short-name-map kms-keywords-map)
        umm-c-lookup-map (generate-lookup-by-umm-c-map kms-keywords-map)
        location-lookup-map (generate-lookup-by-location-map kms-keywords-map)
        measurement-lookup-map (generate-lookup-by-measurement-name kms-keywords-map)]
    (merge kms-keywords-map
           {:short-name-index short-name-lookup-map
            :umm-c-index umm-c-lookup-map
            :locations-index location-lookup-map
            :measurement-index measurement-lookup-map})))

(defn deflate
  "Takes a KMS index and returns a minimal version to store more efficiently and in a way that
  the index can be recreated in a way to reduce the memory usage."
  [kms-index]
  (dissoc kms-index :short-name-index :umm-c-index :locations-index :measurement-index))

(defn lookup-by-short-name
  "Takes a kms-index, the keyword scheme, and a short name and returns the full KMS hierarchy for
  that short name. Comparison is made case insensitively."
  [kms-index keyword-scheme short-name]
  (get-in kms-index [:short-name-index keyword-scheme (util/safe-lowercase short-name)]))

(defn lookup-by-location-string
  "Takes a kms-index and a location string and returns the full KMS hierarchy for that location
  string. Comparison is made case insensitively."
  [kms-index location-string]
  (get-in kms-index [:locations-index (str/upper-case location-string)]))

(defn- remove-long-name-from-kms-index
  "Removes long-name from the umm-c-index keys in order to prevent validation when
   long-name is not present in the umm-c-keyword.  We only want to validate long-name if it is not nil."
  [kms-index keyword-scheme]
  (into {}
    (for [[k v] (get-in kms-index [:umm-c-index keyword-scheme])]
      [(dissoc k :long-name) v])))

(defmulti lookup-by-umm-c-keyword
  "Takes a keyword as represented in UMM-C, UMM-G, UMM-S, UMM-T, or UMM-Var and
  returns the KMS keyword. Comparison is made case insensitively."
  (fn [kms-index keyword-scheme umm-c-keyword]
    keyword-scheme))

(defmethod lookup-by-umm-c-keyword :granule-data-format
  [kms-index keyword-scheme umm-c-keyword]
  (let [comparison-map (normalize-for-lookup
                         (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
                         (kms-scheme->fields-for-umm-c-lookup keyword-scheme))]
    (->> (get kms-index keyword-scheme)
         (filter #(= (str/lower-case (:short-name %))
                     (str/lower-case (:format comparison-map))))
         seq)))

(defmethod lookup-by-umm-c-keyword :platforms
  [kms-index keyword-scheme umm-c-keyword]
  (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
        ;; CMR-3696 This is needed to compare the keyword category, which is mapped
        ;; to the UMM Platform Type field.  This will avoid complications with facets.
        umm-c-keyword (set/rename-keys umm-c-keyword {:type :category})
        comparison-map (normalize-for-lookup umm-c-keyword (kms-scheme->fields-for-umm-c-lookup
                                                            keyword-scheme))]
    (if (get umm-c-keyword :long-name)
      ;; Check both longname and shortname
      (get-in kms-index [:umm-c-index keyword-scheme comparison-map])
      ;; Check just shortname
      (-> kms-index
          (remove-long-name-from-kms-index keyword-scheme)
          (get comparison-map)))))

(defmethod lookup-by-umm-c-keyword :default
  [kms-index keyword-scheme umm-c-keyword]
  (let [umm-c-keyword (csk-extras/transform-keys csk/->kebab-case umm-c-keyword)
        comparison-map (normalize-for-lookup umm-c-keyword
                                             (kms-scheme->fields-for-umm-c-lookup keyword-scheme))]
    (get-in kms-index [:umm-c-index keyword-scheme comparison-map])))

(defn lookup-by-measurement
  [kms-index value]
  (let [{:keys [MeasurementContextMedium MeasurementObject MeasurementQuantities]} value
        measurements (if (seq MeasurementQuantities)
                       (map (fn [quantity]
                              {:context-medium MeasurementContextMedium
                               :object MeasurementObject
                               :quantity (:Value quantity)})
                            MeasurementQuantities)
                       [{:context-medium MeasurementContextMedium
                         :object MeasurementObject}])
        invalid-measurements (remove #(get-in kms-index [:measurement-index :measurement-name %])
                                     measurements)]
    (seq invalid-measurements)))
