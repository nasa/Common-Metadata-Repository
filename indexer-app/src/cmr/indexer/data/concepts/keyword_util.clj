(ns cmr.indexer.data.concepts.keyword-util
  "Contains utility functions for working with keywords when adding data
  to elasticsearch for indexing."
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer [warn]]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.attribute :as attrib]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.umm-spec.location-keywords :as lk]
   [cmr.umm-spec.util :as su]))

;; Aliases for NEAR_REAL_TIME
(def nrt-aliases
  ["near_real_time","nrt","near real time","near-real time","near-real-time","near real-time"])

(def ^:private keywords-separator-regex
  "Defines Regex to split strings with special characters into multiple words for keyword searches."
  #"[!@#$%^&()\-=_+{}\[\]|;'.,\\\"/:<>?`~* ]")

(def keyword-phrase-separator-regex
  "Defines Regex to split strings with special characters into multiple phrases for keyword phrase searches."
  #"[!@#$%^&()\-=_+{}\[\]|;'.,\\\"/:<>?`~*]")

(defn- wrapped-keyword?
  "Checks if the field-value about to be processed is a term surrounded by parens or brackets.
   If it is, we want to add the usual keywords, but also add the unwrapped field-value on its own."
  [field-value]
  (let [first-char (first field-value)
        last-char (last field-value)]
    (and (or (= \( first-char)
             (= \{ first-char)
             (= \[ first-char))
         (or (= \) last-char)
             (= \} last-char)
             (= \] last-char)))))

(defn- prepare-keyword-field
  "Convert a string to lowercase then separate it into keywords"
  [field-value]
  (when field-value
    (let [field-value (string/lower-case field-value)]
      (if (wrapped-keyword? field-value)
        (as-> (subs field-value 1 (dec (.length field-value))) trimmed-value
              (into [field-value trimmed-value] (string/split field-value keywords-separator-regex)))
        (into [field-value] (string/split field-value keywords-separator-regex))))))

(defn field-values->keyword-text
  "Returns the keyword text for the given list of field values."
  [field-values]
  (->> field-values
       (mapcat #(string/split % #" "))
       (mapcat prepare-keyword-field)
       (keep not-empty)
       (apply sorted-set)
       (string/join \space)))

(defn field-values->individual-words
  "Return a list of individual keyword words for the given list of field values."
  [field-values]
  (->> field-values
       (mapcat #(string/split % #" "))
       (mapcat prepare-keyword-field)
       (keep not-empty)
       (apply sorted-set)))

(defn contact-group->keywords
  "Converts a contact group into a vector of terms for keyword searches."
  [contact-group]
  (let [{group-name :GroupName
         roles :Roles} contact-group]
    (concat [group-name]
            roles)))

(defn contact-person->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [contact-person]
  (let [{first-name :FirstName
         last-name :LastName
         roles :Roles} contact-person]
    (concat [first-name last-name]
            roles)))

(defn- get-contact-persons
  "Retrieve a list of contact persons from the given collection."
  [collection]
  (let [{:keys [ContactPersons ContactGroups DataCenters]} collection]
    (concat ContactPersons
            ContactGroups
            (mapcat :ContactGroups DataCenters)
            (mapcat :ContactPersons DataCenters))))

(defn- get-contact-mechanisms->keywords
  "Retrieve contact mechanisms from the given collection
  and convert into a list of terms for keyword searches."
  [collection]
  (map #(:Value (first %))
       (map #(get-in % [:ContactInformation :ContactMechanisms])
            (get-contact-persons collection))))

(defn- data-center->keywords
  "Convert a compound field into a list of terms for keyword searches."
  [data-center]
  (let [{contact-persons :ContactPersons
         contact-groups :ContactGroups} data-center]
    (concat (mapcat contact-person->keywords contact-persons)
            (mapcat contact-group->keywords contact-groups)
            [(:ShortName data-center)])))

(defn- collection-citation->keywords
  "Convert a compound field into a vector of terms for keyword searches."
  [collection-citation]
  [(:Creator collection-citation)
   (:OtherCitationDetails collection-citation)])

(defn- characteristic->keywords
  "Convert a compound field into a vector of terms for keyword searches."
  [characteristic]
  [(:name characteristic)
   (:description characteristic)
   (:value characteristic)])

(defn- collection-platforms->keywords
  "Convert the given platforms to a list of terms for keyword searches."
  [platforms]
  (let [platforms (map util/map-keys->kebab-case
                       (when-not (= su/not-provided-platforms platforms) platforms))
        platform-short-names (map :short-name platforms)
        platform-instruments (mapcat :instruments platforms)
        instruments (concat platform-instruments (mapcat :composed-of platform-instruments))
        instrument-short-names (distinct (keep :short-name instruments))
        instrument-techniques (keep :technique instruments)
        instrument-characteristics (mapcat characteristic->keywords
                                           (mapcat :characteristics instruments))
        platform-characteristics (mapcat characteristic->keywords
                                         (mapcat :characteristics platforms))]
    (concat platform-characteristics
            instrument-characteristics
            instrument-short-names
            instrument-techniques
            platform-short-names)))

(defn names->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{long-name :LongName
         short-name :ShortName} data]
    [long-name
     short-name]))

(defn- additional-attribute->keywords
  "Convert a compound field into a vector of terms for keyword searches."
  [attribute]
  (attrib/aa->keywords (util/map-keys->kebab-case attribute)))

(defn- collection-data-type->keywords
  "Return collection data type keywords."
  [data-type]
  (if (= "NEAR_REAL_TIME" data-type)
    nrt-aliases
    data-type))

(defn- url->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{:keys [Description Subtype Type URLValue URLContentType]} data]
    [Description Subtype Type URLValue URLContentType]))

(defn- related-url->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{:keys [Description Subtype Type URL URLContentType]} data]
    [Description Subtype Type URL URLContentType]))

(defn science-keyword->keywords
  "Converts a science keyword into a vector of terms for keyword searches."
  [science-keyword]
  (let [{:keys [Category DetailedVariable Term Topic VariableLevel1 VariableLevel2 VariableLevel3]} science-keyword]
    [Category DetailedVariable Term Topic VariableLevel1 VariableLevel2 VariableLevel3]))

(defn- tool-keyword->keywords
  "Converts a tool keyword into a vector of terms for keyword searches."
  [tool-keyword]
  (let [{:keys [ToolCategory ToolSpecificTerm ToolTerm ToolTopic]} tool-keyword]
    [ToolCategory ToolSpecificTerm ToolTerm ToolTopic]))

(defn organization->keywords
  "Converts an organization into a vector of terms for keyword searches."
  [organization]
  (let [{roles :Roles} organization]
    (concat (names->keywords organization)
            roles)))

(defn- archive-distribution-data-formats
  "Converts ArchiveFileInformation and DistributionFileInformation Formats into a vector
   of terms for keyword searches."
  [archive-distribution-info]
  (concat
    (->> archive-distribution-info
         :ArchiveAndDistributionInformation
         :FileDistributionInformation
         (map :Format))
    (->> archive-distribution-info
         :ArchiveAndDistributionInformation
         :FileArchiveInformation
         (map :Format))))

(def ^:private variable-fields->fn-mapper
  "A data structure that maps UMM variable field names to functions that
  extract keyword data for those fields. Intended only to be used as part
  of a larger map for multiple field types. By default, the extract keyword
  is the same as the field name. So only fields that has an extract keyword
  different from the field name needs to be listed.

  See `fields->fn-mapper`, below."
  {:ScienceKeywords #(mapcat science-keyword->keywords (:ScienceKeywords %))})

(def ^:private tool-fields->fn-mapper
  "A data structure that maps UMM tool field names to functions that
  extract keyword data for those fields. Intended only to be used as part
  of a larger map for multiple field types.

  See `fields->fn-mapper`, below."
  {:ContactGroups #(mapcat contact-group->keywords (:ContactGroups %))
   :ContactPersons #(mapcat contact-person->keywords (:ContactPersons %))
   :URL #(url->keywords (:URL %))
   :RelatedURLs #(mapcat related-url->keywords (:RelatedURLs %))
   :ToolKeywords #(mapcat tool-keyword->keywords (:ToolKeywords %))
   :Organizations #(mapcat organization->keywords (:Organizations %))})

(defn limit-text-field-length
  "Truncates strings if their length exceeds the given byte limit.  
  Elasticsearch text field mappings have maximum supported sizes based
  on their type. Some fields that are text-only should be truncated."
  [field s max-bytes]
  (when (string? s)
    (if (> (count (.getBytes s "UTF-8")) max-bytes)
      (do
        (warn (format "Text size for %s exceeded the maximum bytes, using first %d characters" field max-bytes))
        (subs s 0 max-bytes))
      s)))

(def ^:private collection-fields->fn-mapper
  "A data structure that maps UMM collection field names to functions that
  extract keyword data for those fields. Intended only to be used as part
  of a larger map for multiple field types.

  See `fields->fn-mapper`, below."
  {:DOI #(get-in % [:DOI :DOI])
   :Abstract #(limit-text-field-length :Abstract (get % :Abstract "") es/MAX_TEXT_UTF8_ENCODING_BYTES)
   :AssociatedDOIs #(mapv :DOI (:AssociatedDOIs %))
   :ProcessingLevel #(get-in % [:ProcessingLevel :Id])
   ;; Simple multi-values data
   :AdditionalAttributes #(mapcat additional-attribute->keywords (:AdditionalAttributes %))
   :CollectionCitations #(mapcat collection-citation->keywords (:CollectionCitations %))
   :CollectionDataType #(collection-data-type->keywords (:CollectionDataType %))
   :CollectionPlatforms #(collection-platforms->keywords (:Platforms %))
   :ContactMechanisms get-contact-mechanisms->keywords
   :DataCenters #(mapcat data-center->keywords (:DataCenters %))
   :DirectoryNames #(mapcat names->keywords (:DirectoryNames %))
   :LocationKeywords #(lk/location-keywords->spatial-keywords-for-indexing (:LocationKeywords %))
   :Projects #(mapcat names->keywords (:Projects %))
   :RelatedUrls #(mapcat related-url->keywords (:RelatedUrls %))
   :TilingIdentificationSystems #(map :TilingIdentificationSystemName (:TilingIdentificationSystems %))
   :ArchiveAndDistributionInformation #(archive-distribution-data-formats %)
   :ScienceKeywords #(mapcat science-keyword->keywords (:ScienceKeywords %))})

(def ^:private fields->fn-mapper
  "A data structure that maps UMM field names to functions that extract keyword
  data for those fields. Intended to be used instead of `case` statements for
  dispatching based upon field name.

  By default, the extract keyword is the same as the field name. So only fields
  that has an extract keyword different from the field name needs to be listed."
  (merge variable-fields->fn-mapper
         tool-fields->fn-mapper
         collection-fields->fn-mapper))

(defn- field-extract-fn
  "Returns the function that will extract the value of the given field from the augmented
  parsed concept.

  For example, to iterate over all the science keywords in a concept and return
  textual data that will be indexd (i.e., from sub-fields):

    (map (field-extract-fn :ScienceKeywords) parsed-concept))"
  [field]
  (get fields->fn-mapper field field))

(defn flatten-collections
  "This function is used to conditionally prepare schema texutal field data,
  in the form of collections of strings or strings, to be used by higher
  order functions that operate on flat collections of textual field data. As
  such, textual data that is not in a collection needs to be converted to
  one. Additionally, any nested collections need to be flattened. Everything
  returned from this function should be a collection of one or more strings."
  [data]
  (if (coll? data)
    (flatten data)
    (vector data)))

(defn concept-key->keywords
  "Given a parsed concept and a single schema key, build a list of keywords
  for that key."
  [parsed-concept schema-key]
  (let [extractor (field-extract-fn schema-key)]
    (->> parsed-concept
         extractor
         flatten-collections
         (remove nil?)
         (remove #(su/default-value? %)))))

(defn concept-keys->keywords
  "Given a parsed concept and a sequence of schema keys, get the keywords
  using all the schema keys."
  [parsed-concept schema-keys]
  (->> schema-keys
       (map (partial concept-key->keywords parsed-concept))
       flatten
       (remove empty?)))

(defn concept-key->keyword-text
  "Given a parsed concept and a single schema key, build a concatenated string
  of keywords for that key."
  [parsed-concept schema-key]
  (field-values->keyword-text
   (concept-key->keywords parsed-concept schema-key)))

(defn concept-keys->keyword-text
  "Given a parsed concept and a sequence of schema keys, build a concatenated
  string of keywords using all the schema keys."
  [parsed-concept schema-keys]
  (field-values->keyword-text
   (concept-keys->keywords parsed-concept schema-keys)))
