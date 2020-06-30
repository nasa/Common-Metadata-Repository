(ns cmr.indexer.data.concepts.service-keyword-util
  "Contains utility functions for working with service keywords when adding data
  to elasticsearch for indexing."
  (:require
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.umm-spec.util :as su]))

(defn- service-url->keywords
  "Converts a compound field into a vector of terms for keyword searches."
  [data]
  (let [{:keys [Description URLValue]} data]
    [Description URLValue]))

(defn- service-keyword->keywords
  "Converts a service keyword into a vector of terms for keyword searches."
  [service-keyword]
  (let [{:keys [ServiceCategory ServiceSpecificTerm ServiceTerm ServiceTopic]} service-keyword]
    [ServiceCategory ServiceSpecificTerm ServiceTerm ServiceTopic]))

(def ^:private service-fields->fn-mapper
  "A data structure that maps UMM service field names to functions that
  extract keyword data for those fields. Intended only to be used as part
  of a larger map for multiple field types.

  See `fields->fn-mapper`, below."
  {:ContactGroups #(mapcat keyword-util/contact-group->keywords (:ContactGroups %))
   :ContactPersons #(mapcat keyword-util/contact-person->keywords (:ContactPersons %))
   :URL #(service-url->keywords (:URL %))
   :ServiceKeywords #(mapcat service-keyword->keywords (:ServiceKeywords %))
   :ServiceOrganizations #(mapcat keyword-util/organization->keywords (:ServiceOrganizations %))})

(defn concept-key->keywords
  "Given a parsed concept and a single schema key, build a list of keywords
  for that key."
  [parsed-concept schema-key]
  (let [extractor (service-fields->fn-mapper schema-key schema-key)]
    (->> parsed-concept
         extractor
         keyword-util/flatten-collections
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

(defn concept-keys->keyword-text
  "Given a parsed concept and a sequence of schema keys, build a concatenated
  string of keywords using all the schema keys."
  [parsed-concept schema-keys]
  (keyword-util/field-values->keyword-text
   (concept-keys->keywords parsed-concept schema-keys)))
