(ns cmr.elastic-utils.mapping
  "Defines different types and functions for defining mappings")

(def string-field-mapping
  {:type "string" :index "not_analyzed"})

(def text-field-mapping
  "Used for analyzed text fields"
  {:type "string"
   ; these fields will be split into multiple terms using the analyzer
   :index "analyzed"
   ; Norms are metrics about fields that elastic can use to weigh certian fields more than
   ; others when computing a document relevance. A typical example is field length - short
   ; fields are weighted more heavily than long feilds. We don't need them for scoring.
   :omit_norms "true"
   ; split the text on whitespace, but don't do any stemmming, etc.
   :analyzer "whitespace"
   ; Don't bother storing term positions or term frequencies in this field
   :index_options "docs"})

(def date-field-mapping
  {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"})

(def double-field-mapping
  {:type "double"})

(def float-field-mapping
  {:type "float"})

(def int-field-mapping
  {:type "integer"})

(def bool-field-mapping
  {:type "boolean"})

(defn stored
  "modifies a mapping to indicate that it should be stored"
  [field-mapping]
  (assoc field-mapping :store "yes"))

(defn not-indexed
  "modifies a mapping to indicate that it should not be indexed and thus is not searchable."
  [field-mapping]
  (assoc field-mapping :index "no"))

(defn doc-values
  "Modifies a mapping to indicate that it should use doc values instead of the field data cache
  for this field.  The tradeoff is slightly slower performance, but the field no longer takes
  up memory in the field data cache.  Only use doc values for fields which require a large
  amount of memory and are not frequently used for sorting."
  [field-mapping]
  (assoc field-mapping :doc_values true))

(defmacro defmapping
  "Defines a new mapping type for an elasticsearch index. The argument after the docstring
  can be used to specify additional top level maping properties.
  Example:
  (defmapping person-mapping :person
    \"Defines a person mapping.\"
    {:name string-field-mapping
     :age int-field-mapping})"
  ([mapping-name mapping-type docstring properties]
   `(defmapping ~mapping-name ~docstring ~mapping-type nil ~properties))
  ([mapping-name mapping-type docstring mapping-settings properties]
   `(def ~mapping-name
      ~docstring
      {~mapping-type
       (merge ~mapping-settings
              {:dynamic "strict"
               :_source {:enabled false}
               :_all {:enabled false}
               :_ttl {:enabled true}
               :properties ~properties})})))

(defmacro defnestedmapping
  "Defines a new nested mapping type for an elasticsearch index. The argument after the
  docstring can be used to specify additional top level maping properties.
  Example:
  (defnestedmapping address-mapping
     \"Defines an address mapping.\"
     {:street string-field-mapping
     :city string-field-mapping})"
  ([mapping-name docstring properties]
   `(defnestedmapping ~mapping-name ~docstring nil ~properties))
  ([mapping-name docstring mapping-settings properties]
   `(def ~mapping-name
      ~docstring
      (merge ~mapping-settings
             {:type "nested"
              :dynamic "strict"
              :_source {:enabled false}
              :_all {:enabled false}
              :properties ~properties}))))

