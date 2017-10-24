(ns cmr.search.services.query-walkers.provider-id-extractor
  "Defines protocols and functions to extract provider ids from the query constructs"
  (:require [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.search.models.query :as qm]
            [cmr.common.concepts :as concepts]))

(defprotocol ExtractProviderIds
  "Defines a function to extract provider-ids"
  (extract-provider-ids
    [c]
    "Extract provider-ids"))

(extend-protocol ExtractProviderIds
  cmr.common_app.services.search.query_model.Query
  (extract-provider-ids
    [query]
    ;; This is expected to the entry way into the
    (let [provider-ids (set (extract-provider-ids (:condition query)))]
      (if (or (provider-ids :none) (provider-ids :any))
        #{}
        provider-ids)))

  cmr.common_app.services.search.query_model.ConditionGroup
  (extract-provider-ids
    [{:keys [operation conditions]}]
    (let [provider-ids (set (mapcat extract-provider-ids conditions))]
      (cond
        (provider-ids :none) [:none]
        (and (= operation :or) (provider-ids :any)) [:any]
        :else
        (disj provider-ids :any))))


  cmr.common_app.services.search.query_model.StringCondition
  (extract-provider-ids
    [{:keys [field value pattern]}]
    (if pattern
      [:any]
      (case field
        :provider [value]
        :collection-concept-id [(concepts/concept-id->provider-id value)]
        :concept-id [(concepts/concept-id->provider-id value)]
        ;;else
        [:any])))

  cmr.common_app.services.search.query_model.StringsCondition
  (extract-provider-ids
    [{:keys [field values]}]
    (case field
      :provider values
      :collection-concept-id (map concepts/concept-id->provider-id values)
      :concept-id (map concepts/concept-id->provider-id values)
      ;;else
      [:any]))

  cmr.search.models.query.CollectionQueryCondition
  (extract-provider-ids
    [_]
    (errors/internal-error! "extract-provider-ids does not support CollectionQueryCondition"))

  ;; catch all extractor
  java.lang.Object
  (extract-provider-ids
    [this]
    [:any]))
