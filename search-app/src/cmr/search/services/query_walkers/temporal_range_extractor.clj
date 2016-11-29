(ns cmr.search.services.query-walkers.temporal-range-extractor
  "Defines protocols and functions to extract keywords from a query."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as cqm]
            [clojure.string :as str]
            [cmr.search.models.query :as qm]))

(defprotocol ExtractTemporalRanges
  "Defines a function to extract temporal ranges"
  (extract-temporal-ranges-seq
    [c]
    "Extracts keywords and keyword like values from keyword conditions and conditions which apply
     to keywords scoring. Returns a sequence of the keywords."))
  ; (contains-keyword-condition?
  ;  [c]
  ;  "Returns true if the query contains a keyword condition?"))

(defn extract-temporal-ranges
  "Extracts keywords and keyword like values from keyword conditions and conditions which apply
   to keywords scoring. Returns a sequence of the distinct keywords or nil if there are none."
  [c]
  (proto-repl.saved-values/save 19)
  (seq (extract-temporal-ranges-seq c)))

(extend-protocol ExtractTemporalRanges
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (extract-temporal-ranges-seq
   [query]
   (extract-temporal-ranges-seq (:condition query)))
  ; (contains-keyword-condition?
  ;  [query]
  ;  (contains-keyword-condition? (:condition query)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (extract-temporal-ranges-seq
   [{:keys [conditions]}]
   (mapcat extract-temporal-ranges-seq conditions))
  ; (contains-keyword-condition?
  ;  [{:keys [conditions]}]
  ;  (reduce (fn [_ condition]
  ;            (when (contains-keyword-condition? condition)
  ;              (reduced true)))
  ;          false
  ;          conditions))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.NestedCondition
  (extract-temporal-ranges-seq
   [{:keys [condition]}]
   (extract-temporal-ranges-seq condition))
  ; (contains-keyword-condition?
  ;  [{:keys [condition]}]
  ;  (contains-keyword-condition? condition))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.TextCondition
  (extract-temporal-ranges-seq
   [{:keys [field query-str]}]
   (when (= field :keyword)
     (extract-temporal-ranges-seq-from-value query-str)))
  ; (contains-keyword-condition?
  ;  [{:keys [field]}]
  ;  (= field :keyword))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.StringCondition
  (extract-temporal-ranges-seq
   [{:keys [field value]}]
   (when (contains? keyword-string-fields field)
     (extract-temporal-ranges-seq-from-value value)))
  ; (contains-keyword-condition?
  ;  [_]
  ;  false)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.StringsCondition
  (extract-temporal-ranges-seq
   [{:keys [field values]}]
   (when (contains? keyword-string-fields field)
     (mapcat extract-temporal-ranges-seq-from-value values)))
  ; (contains-keyword-condition?
  ;  [_]
  ;  false)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.CollectionQueryCondition
  (extract-temporal-ranges-seq
   [_]
   (errors/internal-error! "extract-temporal-ranges-seq does not support CollectionQueryCondition"))
  ; (contains-keyword-condition?
  ;  [_]
  ;  (errors/internal-error! "contains-keyword-condition? does not support CollectionQueryCondition"))

  ;; catch all extractor
  java.lang.Object
  (extract-temporal-ranges-seq
   [this]
   nil))
  ; (contains-keyword-condition?
  ;  [this]
  ;  false))
