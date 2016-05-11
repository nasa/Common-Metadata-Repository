(ns cmr.search.services.query-walkers.keywords-extractor
  "Defines protocols and functions to extract keywords from a query."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as cqm]
            [clojure.string :as str]
            [cmr.search.models.query :as qm]))

(defprotocol ExtractKeywords
  "Defines a function to extract keywords"
  (extract-keywords-seq
    [c]
    "Extracts keywords and keyword like values from keyword conditions and conditions which apply
     to keywords scoring. Returns a sequence of the keywords."))

(defn extract-keywords
  "Extracts keywords and keyword like values from keyword conditions and conditions which apply
   to keywords scoring. Returns a sequence of the distinct keywords or nil if there are none."
  [c]
  (when-let [keywords (seq (extract-keywords-seq c))]
    (distinct keywords)))

(def ^:private keyword-string-fields
  "This defines the set of string condition fields that we will extract keyword terms from. Any condition
   that's a string condition in this list will be a source of keywords that are used to adjust the
   score and determine if we're sorting by relevance."
  #{:project
    :platform
    :instrument
    :sensor
    :science-keywords.category
    :science-keywords.topic
    :science-keywords.term
    :science-keywords.variable-level-1
    :science-keywords.variable-level-2
    :science-keywords.variable-level-3
    :science-keywords.any
    :two-d-coordinate-system-name
    :processing-level-id
    :data-center
    :archive-center})

(defn- extract-keywords-seq-from-value
  "Converts value to lower case and splits on whitespace to create a list of keywords"
  [value]
  (-> value str/lower-case (str/split #"\s+")))

(extend-protocol ExtractKeywords
  cmr.common_app.services.search.query_model.Query
  (extract-keywords-seq
    [query]
    (extract-keywords-seq (:condition query)))

  cmr.common_app.services.search.query_model.ConditionGroup
  (extract-keywords-seq
    [{:keys [conditions]}]
    (mapcat extract-keywords-seq conditions))

  cmr.common_app.services.search.query_model.NestedCondition
  (extract-keywords-seq
    [{:keys [condition]}]
    (extract-keywords-seq condition))

  cmr.common_app.services.search.query_model.TextCondition
  (extract-keywords-seq
    [{:keys [field query-str]}]
    (when (= field :keyword)
      (extract-keywords-seq-from-value query-str)))

  cmr.common_app.services.search.query_model.StringCondition
  (extract-keywords-seq
    [{:keys [field value]}]
    (when (contains? keyword-string-fields field)
      (extract-keywords-seq-from-value value)))

  cmr.common_app.services.search.query_model.StringsCondition
  (extract-keywords-seq
    [{:keys [field values]}]
    (when (contains? keyword-string-fields field)
      (mapcat extract-keywords-seq-from-value values)))

  cmr.search.models.query.CollectionQueryCondition
  (extract-keywords-seq
    [_]
    (errors/internal-error! "extract-keywords-seq does not support CollectionQueryCondition"))

  ;; catch all extractor
  java.lang.Object
  (extract-keywords-seq
    [this]
    nil))

