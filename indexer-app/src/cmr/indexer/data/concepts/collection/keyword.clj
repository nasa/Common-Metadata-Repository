(ns cmr.indexer.data.concepts.collection.keyword
  "Contains functions to create keyword fields"
  (:require
   [clojure.string :as str]
   [cmr.common.concepts :as concepts]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]))

(defn create-keywords-field
  [concept-id collection other-fields to-keyword-text?]
  "Create a keyword field for keyword searches by concatenating several other fields
  into a single string"
  (let [{:keys [platform-long-names instrument-long-names entry-id]} other-fields
        provider-id (:provider-id (concepts/parse-concept-id concept-id))
        schema-keys [:Abstract
                     :AncillaryKeywords
                     :AdditionalAttributes
                     :AssociatedDOIs
                     :CollectionCitations
                     :CollectionDataType
                     :ContactGroups
                     :ContactMechanisms
                     :ContactPersons
                     :DataCenters
                     :DirectoryNames
                     :DOI
                     :EntryTitle
                     :ISOTopicCategories
                     :LocationKeywords
                     :CollectionPlatforms
                     :ProcessingLevel
                     :Projects
                     :RelatedUrls
                     :ScienceKeywords
                     :ShortName
                     :TemporalKeywords
                     :TilingIdentificationSystems
                     :Version
                     :VersionDescription
                     :ArchiveAndDistributionInformation]
        keywords (concat
                  instrument-long-names
                  platform-long-names
                  [concept-id]
                  [entry-id]
                  [provider-id]
                  (keyword-util/concept-keys->keywords collection schema-keys))]
    (if to-keyword-text?
      (keyword-util/field-values->keyword-text keywords)
      (map #(str/lower-case %) keywords))))
