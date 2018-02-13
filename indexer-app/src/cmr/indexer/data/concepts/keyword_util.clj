(ns cmr.indexer.data.concepts.keyword-util
  "Contains utility functions for working with keywords when adding data
  to elasticsearch for indexing."
  (:require
   [clojure.string :as string]))

(def ^:private keywords-separator-regex
  "Defines Regex to split strings with special characters into multiple words for keyword searches."
  #"[!@#$%^&()\-=_+{}\[\]|;'.,\\\"/:<>?`~* ]")

(defn- prepare-keyword-field
  [field-value]
  "Convert a string to lowercase then separate it into keywords"
  (when field-value
    (let [field-value (string/lower-case field-value)]
      (into [field-value] (string/split field-value keywords-separator-regex)))))

(defn field-values->keyword-text
  "Returns the keyword text for the given list of field values."
  [field-values]
  (->> field-values
       (mapcat prepare-keyword-field)
       (keep not-empty)
       (apply sorted-set)
       (string/join \space)))

(defn contact-group->keywords
  "Converts a contact group into a vector of terms for keyword searches."
  [contact-group]
  (let [{group-name :GroupName
         roles :Roles} contact-group]
    (concat [group-name]
            roles)))

(defn contact-person->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{first-name :FirstName
         last-name :LastName
         roles :Roles} data]
    (concat [first-name last-name]
            roles)))

(defn names->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{long-name :LongName
         short-name :ShortName} data]
    [long-name
     short-name]))

(defn platform->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{instruments :Instruments} data]
    (concat (names->keywords data)
            (mapcat names->keywords instruments))))

(defn related-url->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{description :Description
         subtype :Subtype
         type :Type
         url :URL
         url-content-type :URLContentType} data]
    [description
     subtype
     type
     url
     url-content-type]))

(defn science-keyword->keywords
  "Converts a science keyword into a vector of terms for keyword searches."
  [science-keyword]
  (let [{category :Category
         detailed-variable :DetailedVariable
         term :Term
         topic :Topic
         variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2
         variable-level-3 :VariableLevel3} science-keyword]
    [category
     detailed-variable
     term
     topic
     variable-level-1
     variable-level-2
     variable-level-3]))

(defn service-keyword->keywords
  "Converts a service keyword into a vector of terms for keyword searches."
  [service-keyword]
  (let [{service-category :ServiceCategory
         service-specific-term :ServiceSpecificTerm
         service-term :ServiceTerm
         service-topic :ServiceTopic} service-keyword]
    [service-category
     service-specific-term
     service-term
     service-topic]))

(defn service-organization->keywords
  "Converts a service keyword into a vector of terms for keyword searches."
  [service-organization]
  (let [{roles :Roles
         service-contact-persons :ContactPersons} service-organization]
    (concat (names->keywords service-organization)
            (mapcat contact-person->keywords service-contact-persons)
            roles)))

(def fields->fn-mapper
  "A data structure that maps UMM field names to functions that extract keyword
  data for those fields. Intended to be used instead of `case` statements for
  dispatching based upon field name.

  For example, to iterate over all the science keywords in a concept and return
  textual data that will be indexd (i.e., from sub-fields):

    (map (:ScienceKeywords fields->fn-mapper) parsed-concept))"
  {;; Simple single-valued data
   :variable-name :variable-name
   :measurement :measurement
   :LongName :LongName
   :Name :Name
   :Version :Version
   ;; Nested single-value data
   :RelatedURL #(related-url->keywords (:RelatedURL %))
   ;; Simple multi-valued data
   :AncillaryKeywords :AncillaryKeywords
   :ContactGroups #(mapcat contact-group->keywords (:ContactGroups %))
   :ContactPersons #(mapcat contact-person->keywords (:ContactPersons %))
   :Platforms #(mapcat platform->keywords (:Platforms %))
   :ScienceKeywords #(mapcat science-keyword->keywords (:ScienceKeywords %))
   :ServiceKeywords #(mapcat service-keyword->keywords (:ServiceKeywords %))
   :ServiceOrganizations #(mapcat service-organization->keywords (:ServiceOrganizations %))})

(defn flatten-collections
  [data]
  (if (coll? data)
    (flatten data)
    (vector data)))

(defn concept-key->keywords
  "Given a parsed concept and a single schema key, build a list of keywords
  for that key."
  [parsed-concept schema-key]
  (let [extractor (schema-key fields->fn-mapper)]
    (->> parsed-concept
         extractor
         flatten-collections
         (remove nil?))))

(defn concept-keys->keywords
  "Given a parsed concept and a sequence of schema keys, get the keywords
  using all the schema keys."
  [parsed-concept schema-keys]
  (->> schema-keys
       (map (partial concept-key->keywords parsed-concept))
       flatten
       (remove empty?)))

(defn concept-key->keyword-text
  [parsed-concept schema-key]
  (field-values->keyword-text
   (concept-key->keywords parsed-concept schema-key)))

(defn concept-keys->keyword-text
  [parsed-concept schema-keys]
  (field-values->keyword-text
   (concept-keys->keywords parsed-concept schema-keys)))
