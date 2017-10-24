(ns cmr.indexer.data.concepts.collection.keyword
  "Contains functions to create keyword fields"
  (:require
    [cmr.common.concepts :as concepts]
    [cmr.common.util :as util]
    [cmr.indexer.data.concepts.attribute :as attrib]
    [cmr.indexer.data.concepts.collection.data-center :as data-center]
    [cmr.indexer.data.concepts.keyword-util :as keyword-util]
    [cmr.indexer.data.concepts.science-keyword-util :as science-keyword-util]
    [cmr.umm-spec.location-keywords :as lk]
    [cmr.umm-spec.util :as su]))

;; NOTE -  The following fields are marked as deprecated in the UMM documenation
;; and are therefore not used for keyword searches in the CMR:
;;    SuggestedUsage (ECHO10)
;;

;; Aliases for NEAR_REAL_TIME
(def nrt-aliases
  ["near_real_time","nrt","near real time","near-real time","near-real-time","near real-time"])

(defn get-contact-persons
  "Return a collection of personnel names and contact mechanisms out of:
  ContactPersons, ContactGroups, and DataCenters"
  [collection]
  (let [{:keys [ContactPersons ContactGroups DataCenters]} collection]
   (concat ContactPersons ContactGroups
    (mapcat :ContactGroups DataCenters)
    (mapcat :ContactPersons DataCenters))))

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
         related-urls :RelatedUrls} collection
        doi (get-in collection [:DOI :DOI])
        processing-level-id (get-in collection [:ProcessingLevel :Id])
        processing-level-id (when-not (= su/not-provided processing-level-id)
                              processing-level-id)
        detailed-locations (map :DetailedLocation (:LocationKeywords collection))
        spatial-keywords (lk/location-keywords->spatial-keywords-for-indexing
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
        contact-persons (get-contact-persons collection)
        contact-personnel-first-names (map :FirstName contact-persons)
        contact-personnel-last-names (remove su/default-value? (map :LastName contact-persons))
        contact-group-names (remove su/default-value? (map :GroupName contact-persons))
        contact-mechanisms (map #(:Value (first %))
                                (map #(get-in % [:ContactInformation :ContactMechanisms]) contact-persons))
        platforms (map util/map-keys->kebab-case
                       (when-not (= su/not-provided-platforms platforms) platforms))
        platform-short-names (map :short-name platforms)
        platform-instruments (mapcat :instruments platforms)
        instruments (concat platform-instruments (mapcat :composed-of platform-instruments))
        instrument-short-names (distinct (keep :short-name instruments))
        instrument-techiques (keep :technique instruments)
        plat-characteristics (mapcat :characteristics platforms)
        plat-char-names (keep :name plat-characteristics)
        plat-char-descs (keep :description plat-characteristics)
        plat-char-values (keep :value plat-characteristics)
        instr-characteristics (mapcat :characteristics instruments)
        instr-char-names (keep :name instr-characteristics)
        instr-char-descs (keep :description instr-characteristics)
        instr-char-values (keep :value instr-characteristics)
        two-d-coord-names (map :TilingIdentificationSystemName
                               (:TilingIdentificationSystems collection))
        data-centers (map :ShortName (:DataCenters collection))
        science-keywords (mapcat science-keyword-util/science-keyword->keywords
                                 (:ScienceKeywords collection))
        attrib-keywords (mapcat #(attrib/aa->keywords (util/map-keys->kebab-case %))
                                (:AdditionalAttributes collection))
        related-url-urls (map :URL related-urls)
        related-url-titles (map :Title related-urls)
        related-url-descriptions (map :Description related-urls)
        ;; Pull author info from both creator and other citation details
        authors (concat
                 (keep :Creator (:CollectionCitations collection))
                 (keep :OtherCitationDetails (:CollectionCitations collection)))
        all-fields (flatten (conj [concept-id]
                                  ancillary-keywords
                                  attrib-keywords
                                  plat-char-descs
                                  plat-char-names
                                  plat-char-values
                                  instr-char-descs
                                  instr-char-names
                                  instr-char-values
                                  collection-data-type
                                  contact-personnel-first-names
                                  contact-personnel-last-names
                                  contact-group-names
                                  contact-mechanisms
                                  data-centers
                                  directory-long-names
                                  directory-short-names
                                  doi
                                  entry-id
                                  entry-title
                                  instrument-long-names
                                  instrument-short-names
                                  instrument-techiques
                                  iso-topic-categories
                                  platform-long-names
                                  platform-short-names
                                  processing-level-id
                                  project-long-names
                                  project-short-names
                                  provider-id
                                  related-url-descriptions
                                  related-url-titles
                                  related-url-urls
                                  science-keywords
                                  short-name
                                  detailed-locations
                                  spatial-keywords
                                  summary
                                  temporal-keywords
                                  two-d-coord-names
                                  version-description
                                  version-id
                                  authors))]
    (keyword-util/field-values->keyword-text all-fields)))
