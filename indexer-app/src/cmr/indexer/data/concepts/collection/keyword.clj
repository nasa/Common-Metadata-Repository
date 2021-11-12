(ns cmr.indexer.data.concepts.collection.keyword
  "Contains functions to create keyword fields"
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]))

(def ^:private max-keyword-word-count
  "Maximum keyword count that should be put into a keyword value."
  64)

(defn ^:private segment-keywords
  "Segment large keyword phrases into chunks of a maximum size."
  [keyword-phrase max-terms]
  (let [terms (string/split keyword-phrase #"\s")]
    (if (> (count terms) max-terms)
      (map #(string/join " " %)
           (partition-all max-terms terms))
      [keyword-phrase])))

(def ^:private trim-and-lowercase
  (comp string/lower-case string/trim))

(defn create-keywords-field
  "Create a keyword field for keyword searches by concatenating 4 group of fields together:
  1. All the keyword fields, unprocessed, for general keyword phrase search.
  2. All the keyword fields, split on a set of special characters, for keyword phrase search when
     the phrase exists in between special characters.
  3. All the keyword fields, split on parens and brackets, for keyword phrase search when the phrase
     exists in the parents or brackets, but contains some other special characters.
  4. All the keyword fields, split into individual words, same as the original keyword search case.
  The new way of indexing support both quoted and unquoted keyword searches."
  [concept-id collection other-fields]
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
        keywords (->> (concat
                       instrument-long-names
                       platform-long-names
                       [concept-id]
                       [entry-id]
                       [provider-id]
                       (keyword-util/concept-keys->keywords collection schema-keys))
                      (remove string/blank?)
                      distinct)
        ;; split each keyword on special characters and extract out phrases surrounded by special characters.
        sp-phrases (mapcat #(string/split % keyword-util/keyword-phrase-separator-regex) keywords)
        ;; split each keyword on parens and brackets only and extract out the word/phrase inside that might contain
        ;; other special characters like in "(merry-go-round)" where  "-" is considered special character.
        paren-bracket-phrases (mapcat #(flatten (re-seq #"[\{\(\[\"“\:_](.*?)[_\:”\"\}\)\)]" %)) keywords)
        ;; the index needed for the original unquoted keyword search.
        keywords-in-words (keyword-util/field-values->individual-words keywords)]
    ;; Lower-case the keywords and add a space at the beginning and the end of each field
    ;; to help with partial keyword phrase matching: i.e. we don't need to distinguish if the match is at the beginning,
    ;; or in the middle or at the end of the keyword field, we just need to match using general expression "* phrase *".
    (->> keywords
         ;; need to index additional keyword phrases that are surrounded by special characters because "* phrase *"
         ;; won't find a match in keyword fields directly because of the extra space added to generalize the search.
         (concat (keep not-empty sp-phrases))
         (concat (keep not-empty paren-bracket-phrases))
         (map trim-and-lowercase)
         (map #(str " " % " "))
         ;; The keyword-in-words here are used for unquoted keyword search. It's exactly the same as the existing
         ;; keyword index fields, without the need to go through a whitespace analyzer.
         (concat keywords-in-words)
         (mapcat #(segment-keywords % max-keyword-word-count))
         (remove string/blank?)
         distinct)))
