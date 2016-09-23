(ns cmr.indexer.data.concepts.keyword
  "Contains functions to create keyword fields"
  (:require 
    [clojure.set :as set]
    [clojure.string :as str]
    [cmr.common.concepts :as concepts]
    [cmr.common.util :as util]
    [cmr.indexer.data.concepts.attribute :as attrib]
    [cmr.indexer.data.concepts.organization :as org]
    [cmr.indexer.data.concepts.science-keyword :as sk] 
    [cmr.umm-spec.location-keywords :as lk]
    [cmr.umm-spec.util :as su]))

;; NOTE -  The following fields are marked as deprecated in the UMM documenation
;; and are therefore not used for keyword searches in the CMR:
;;    SuggestedUsage (ECHO10)
;;

;; Regex to split strings with special characters into multiple words for keyword searches
(def keywords-separator-regex #"[!@#$%^&()\-=_+{}\[\]|;'.,\\\"/:<>?`~* ]")

;; Aliases for NEAR_REAL_TIME
(def nrt-aliases
  ["near_real_time","nrt","near real time","near-real time","near-real-time","near real-time"])

(defn prepare-keyword-field
  [field-value]
  "Convert a string to lowercase then separate it into keywords"
  (when field-value
    (let [field-value (str/lower-case field-value)]
      (into [field-value] (str/split field-value keywords-separator-regex)))))

(defn create-keywords-field
  [concept-id collection umm-spec-collection other-fields]
  "Create a keyword field for keyword searches by concatenating several other fields
  into a single string"
  (let [;; TODO version-description will be added to UMM-SPEC in CMR-3241
        {:keys [version-description]} (:product collection)
        {:keys [platform-long-names instrument-long-names entry-id]} other-fields
        {short-name :ShortName version-id :Version entry-title :EntryTitle
         collection-data-type :CollectionDataType summary :Abstract 
         temporal-keywords :TemporalKeywords} umm-spec-collection
        processing-level-id (get-in umm-spec-collection [:ProcessingLevel :Id]) 
        processing-level-id (when-not (= su/not-provided processing-level-id)
                              processing-level-id)
        spatial-keywords (lk/location-keywords->spatial-keywords
                           (:LocationKeywords umm-spec-collection))
        projects (for [{:keys [ShortName LongName]} (:Projects umm-spec-collection)]
                   {:short-name ShortName :long-name LongName})
        provider-id (:provider-id (concepts/parse-concept-id concept-id))
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               nrt-aliases
                               collection-data-type)
        project-long-names (map :long-name projects)
        project-short-names (map :short-name projects)
        platforms (let [pfs (:Platforms umm-spec-collection)]
                    (when-not (= su/not-provided-platforms pfs) pfs)) 
        platforms (map #(util/map-keys->kebab-case %) platforms)
        platform-short-names (map :short-name platforms)
        instruments (mapcat :instruments platforms)
        instrument-short-names (keep :short-name instruments)
        instrument-techiques (keep :technique instruments)
        sensors (mapcat :sensors instruments)
        sensor-short-names (keep :short-name sensors)
        sensor-long-names (keep :long-name sensors)
        sensor-techniques (keep :technique sensors)
        characteristics (mapcat :characteristics platforms)
        char-names (keep :name characteristics)
        char-descs (keep :description characteristics)
        two-d-coord-names (map :TilingIdentificationSystemName
                               (:TilingIdentificationSystems umm-spec-collection)) 
        data-centers (map :ShortName (:DataCenters umm-spec-collection))
        science-keywords (mapcat #(sk/science-keyword->keywords (util/map-keys->kebab-case %))
                                 (:ScienceKeywords umm-spec-collection)) 
        attrib-keywords (mapcat #(attrib/psa->keywords (util/map-keys->kebab-case %))
                                (:AdditionalAttributes umm-spec-collection))
        all-fields (flatten (conj [concept-id]
                                  provider-id
                                  entry-title
                                  collection-data-type
                                  short-name
                                  entry-id
                                  two-d-coord-names
                                  summary
                                  version-id
                                  version-description
                                  processing-level-id
                                  data-centers
                                  science-keywords
                                  attrib-keywords
                                  spatial-keywords
                                  temporal-keywords
                                  project-long-names
                                  project-short-names
                                  platform-short-names
                                  platform-long-names
                                  instrument-short-names
                                  instrument-long-names
                                  instrument-techiques
                                  sensor-short-names
                                  sensor-long-names
                                  sensor-techniques
                                  char-names
                                  char-descs))
        split-fields (set (mapcat prepare-keyword-field all-fields))]
    (str/join " " split-fields)))
