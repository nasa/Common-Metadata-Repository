(ns cmr.search.services.json-parameters.conversion
  "Contains functions for parsing and converting JSON query parameters to query conditions"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.common.util :as u]
            [cmr.search.services.parameters.legacy-parameters :as lp]
            [cmr.common.concepts :as cc]
            [cmr.common.date-time-parser :as parser]
            [cmr.search.services.parameters.conversion :as pc]
            [cmr.search.services.parameters.parameter-validation :as pv]
            [cheshire.core :as json]))

(def concept-param->type
  "A mapping of param names to query condition types based on concept-type"
  {:collection {:entry-title :string
                :entry-id :string
                :provider :string
                :or :or
                :and :and
                :not :not}})

(defn- param-name->type
  "Returns the query condition type based on the given concept-type and param-name."
  [concept-type param-name]
  (get-in concept-param->type [concept-type param-name]))

(defmulti json-parameter->condition
  "Converts a JSON parameter into a condition"
  (fn [concept-type param value]
    (param-name->type concept-type param)))

(defmethod json-parameter->condition :default
  [concept-type param value]
  (errors/internal-error!
    (format "Could not find parameter handler for [%s] with concept-type [%s]"
            param concept-type)))

(defmethod json-parameter->condition :string
  [concept-type param value]
  ;; TODO handle case sensitivity and wildcards
  (qm/string-condition param value false false))

;; Example {"and": [{"entry-title": "ET", "provider": "PROV1"}
;;                 {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod json-parameter->condition :and
  [concept-type param values]
  (gc/and-conds
    (for [value values]
      (gc/and-conds
        (for [[k v] value]
          (json-parameter->condition concept-type k v))))))

;; Example {"or": [{"entry-title": "ET", "provider": "PROV1"}
;;                 {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod json-parameter->condition :or
  [concept-type param values]
  (gc/or-conds
    (for [value values]
      (gc/and-conds
        (for [[k v] value]
          (json-parameter->condition concept-type k v))))))

(defmethod json-parameter->condition :not
  [concept-type param value]
  (gc/or-conds
    (map (fn [[exclude-param exclude-val]]
           (qm/map->NegatedCondition
             {:condition (json-parameter->condition concept-type exclude-param exclude-val)}))
         value)))

(defn- json-query->query-condition
  "Converts a JSON query into a query condition."
  [concept-type json-query]
  (let [query-conditions (concat (for [[k v] json-query]
                                   (json-parameter->condition concept-type k v)))]
    (when (seq query-conditions) (gc/and-conds query-conditions))))

(defn json-parameters->query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-aql-or-json-parameters concept-type params)
        json-query (u/map-keys->kebab-case (json/parse-string json-string true))]
    (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
                     :concept-type concept-type
                     :condition (json-query->query-condition concept-type json-query)))))

