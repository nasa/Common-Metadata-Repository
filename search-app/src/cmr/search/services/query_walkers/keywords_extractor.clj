(ns cmr.search.services.query-walkers.keywords-extractor
  "Defines protocols and functions to extract keywords from a query."
  (:require
   [clojure.string :as str]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as qm]))

(defprotocol ExtractKeywords
  "Defines a function to extract keywords"
  (extract-keywords-seq
    [c]
    "Extracts keywords and keyword like values from keyword conditions and conditions which apply
     to keywords scoring. Returns 2 sequences of keywords - one list from the
     keyword condition and one from fields in the format {:keywords [...] :field-keywords [...]}")
  (contains-keyword-condition?
   [c]
   "Returns true if the query contains a keyword condition?"))

(defn extract-keywords
  "Extracts keywords and keyword like values from keyword conditions and conditions which apply
   to keywords scoring. Returns 2 sequences of distinct keywords - one list from the
   keyword condition and one from fields. The lists are nil if no keywords exist.
   The format is {:keywords [...] :field-keywords [...]}"
  [c]
  (let [keywords (extract-keywords-seq c)]
    (when (or (seq (:keywords keywords)) (seq (:field-keywords keywords)))
      (-> keywords
          (update :keywords #(seq (distinct %)))
          (update :field-keywords #(seq (distinct %)))))))

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

(defn- extract-keywords-seq-from-keyword-value
  "Converts keyword value to lower case and splits on whitespace to create a list of keywords.
  Or if the value is a double quoted string, do not split on whitespace."
  [value]
  (let [t-value (str/trim value)]
    (if (and (str/starts-with? t-value "\"") (str/ends-with? t-value "\""))
      (-> t-value str/lower-case (str/replace #"^\"|\"$" "") vector)
      (-> value str/lower-case (str/split #"\s+")))))

(extend-protocol ExtractKeywords
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (extract-keywords-seq
   [query]
   (extract-keywords-seq (:condition query)))
  (contains-keyword-condition?
   [query]
   (contains-keyword-condition? (:condition query)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (extract-keywords-seq
   [{:keys [conditions]}]
   (apply merge-with concat (map extract-keywords-seq conditions)))
  (contains-keyword-condition?
   [{:keys [conditions]}]
   (reduce (fn [_ condition]
             (when (contains-keyword-condition? condition)
               (reduced true)))
           false
           conditions))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.NestedCondition
  (extract-keywords-seq
   [{:keys [condition]}]
   (extract-keywords-seq condition))
  (contains-keyword-condition?
   [{:keys [condition]}]
   (contains-keyword-condition? condition))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.TextCondition
  (extract-keywords-seq
   [{:keys [field query-str]}]
   (when (= field :keyword)
     {:keywords (extract-keywords-seq-from-keyword-value query-str)}))
  (contains-keyword-condition?
   [{:keys [field]}]
   (= field :keyword))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.StringCondition
  (extract-keywords-seq
   [{:keys [field value]}]
   (when (contains? keyword-string-fields field)
     {:field-keywords (extract-keywords-seq-from-value value)}))
  (contains-keyword-condition?
   [_]
   false)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.StringsCondition
  (extract-keywords-seq
   [{:keys [field values]}]
   (when (contains? keyword-string-fields field)
     {:field-keywords (mapcat extract-keywords-seq-from-value values)}))
  (contains-keyword-condition?
   [_]
   false)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.CollectionQueryCondition
  (extract-keywords-seq
   [_]
   (errors/internal-error! "extract-keywords-seq does not support CollectionQueryCondition"))
  (contains-keyword-condition?
   [_]
   (errors/internal-error! "contains-keyword-condition? does not support CollectionQueryCondition"))

  ;; catch all extractor
  java.lang.Object
  (extract-keywords-seq
   [this]
   nil)
  (contains-keyword-condition?
   [this]
   false))
