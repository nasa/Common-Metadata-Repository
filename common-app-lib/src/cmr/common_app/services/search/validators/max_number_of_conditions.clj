(ns cmr.common-app.services.search.validators.max-number-of-conditions
  "Validates that a query does not contain more than the configured maximum number of conditions"
  (:require
   [cmr.common-app.services.search.query-model]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [debug info]]))

(defconfig max-number-of-conditions
  "The configured maximum number of conditions in a query"
  {:default 4100
   :type Long})

(defprotocol ConditionCounter
  (count-conditions
    [c]
    "Returns the number of conditions in the query object"))

(extend-protocol ConditionCounter
  cmr.common_app.services.search.query_model.ConditionGroup
  (count-conditions
    [{:keys [conditions]}]
    (reduce + (map count-conditions conditions)))

  ;; catch all extractor
  java.lang.Object
  (count-conditions
    [this]
    (if-let [c (:condition this)]
      (count-conditions c)
      1)))

(defn validate
  "Validates that a query does not contain more than the configured maximum number of conditions"
  [query]
  (let [num-conditions (count-conditions query)]
    (when (> num-conditions 50)
      (info "Query contained" num-conditions "conditions"))
    (when (> num-conditions (max-number-of-conditions))
      [(format (str "The number of conditions in the query [%d] exceeded the maximum allowed for a "
                    "query [%s]. Reduce the number of conditions in your query.")
               num-conditions (max-number-of-conditions))])))
