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
  into a single string if to-keyword-text? is true. Otherwise, create a keyword-phrase field
  where we want to leave the field values in a list, unprocessed, except for lower-case them."
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
                  (keyword-util/concept-keys->keywords collection schema-keys))
        keywords-in-words (keyword-util/field-values->individual-words keywords)]
    (if to-keyword-text?
      (keyword-util/field-values->keyword-text keywords)
      ;; keyword phrase index case. Lower-case the keywords and add a space at the beginning and the end of each field
      ;; to help with partial keyword phrase matching: i.e. we don't need to distinguish if the match is at the beginning, 
      ;; or in the middle or at the end of the keyword field, we just need to match using general expression "* phrase *". 
      (->> keywords
           ;; The first keywords-in-words here are used for keyword phrase search for "example" if a field
           ;; contains "(example)". All the keyword phrase searches will be like "* phrase *", in order to match
           ;; partially on word boundary. So, only the index with these individual words with added spaces on both
           ;; ends can match the query. 
           (concat keywords-in-words)
           (map #(str/lower-case %))
           (map #(str " " % " "))
           ;; The second keyword-in-words here are used for unquoted keyword search. It's exactly the same as the current
           ;; keyword index fields, except that since the keyword phrase index mapping is not using whitespace analyzer,
           ;; we don't need to join them all to spaced delimited strings and rely on the analyzer to separate them and index.   
           (concat keywords-in-words)))))
