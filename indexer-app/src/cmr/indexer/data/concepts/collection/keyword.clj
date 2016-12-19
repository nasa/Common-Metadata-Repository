(ns cmr.indexer.data.concepts.collection.keyword
  "Contains functions to create keyword fields"
  (:require
    [clojure.string :as str]
    [cmr.common.concepts :as concepts]
    [cmr.common.util :as util]
    [cmr.indexer.data.concepts.attribute :as attrib]
    [cmr.indexer.data.concepts.collection.science-keyword :as sk]
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
  [concept-id collection other-fields]
  "Create a keyword field for keyword searches by concatenating several other fields
  into a single string"
  (let [{:keys [platform-long-names instrument-long-names entry-id]} other-fields
        {short-name :ShortName version-id :Version entry-title :EntryTitle
         collection-data-type :CollectionDataType summary :Abstract
         temporal-keywords :TemporalKeywords platforms :Platforms
         ancillary-keywords :AncillaryKeywords
         directory-names :DirectoryNames
         iso-topic-categories :ISOTopicCategories
         version-description :VersionDescription
         related-urls :RelatedUrls
         contacts :ContactPersons} collection
        processing-level-id (get-in collection [:ProcessingLevel :Id])
        processing-level-id (when-not (= su/not-provided processing-level-id)
                              processing-level-id)
        spatial-keywords (lk/location-keywords->spatial-keywords
                          (:LocationKeywords collection))
        projects (for [{:keys [ShortName LongName]} (:Projects collection)]
                   {:short-name ShortName :long-name LongName})
        provider-id (:provider-id (concepts/parse-concept-id concept-id))
        collection-data-type (if (= "NEAR_REAL_TIME" collection-data-type)
                               nrt-aliases
                               collection-data-type)
        project-long-names (map :long-name projects)
        project-short-names (map :short-name projects)
        directory-long-names (map :LongName directory-names)
        directory-short-names (map :ShortName directory-names)
        personnel-first-names (map :FirstName contacts)
        personnel-last-names (map :LastName contacts)
        ;; The (map #(get-in returns the vector of contacts inside of a list, hence the use of (first
        contact-mechanisms (map :Value (first (map #(get-in % [:ContactInformation :ContactMechanisms]) contacts)))
        platforms (map util/map-keys->kebab-case
                       (when-not (= su/not-provided-platforms platforms) platforms))
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
                               (:TilingIdentificationSystems collection))
        data-centers (map :ShortName (:DataCenters collection))
        science-keywords (mapcat sk/science-keyword->keywords (:ScienceKeywords collection))
        attrib-keywords (mapcat #(attrib/psa->keywords (util/map-keys->kebab-case %))
                                (:AdditionalAttributes collection))
        related-url-urls (mapcat :URLs related-urls)
        related-url-titles (map :Title related-urls)
        related-url-descriptions (map :Description related-urls)
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
                                  personnel-first-names
                                  personnel-last-names
                                  contact-mechanisms
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
                                  char-descs
                                  ancillary-keywords
                                  directory-long-names
                                  directory-short-names
                                  iso-topic-categories
                                  related-url-urls
                                  related-url-titles
                                  related-url-descriptions))
        split-fields (set (mapcat prepare-keyword-field all-fields))]
    (str/join " " split-fields)))
