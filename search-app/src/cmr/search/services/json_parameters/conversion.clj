(ns cmr.search.services.json-parameters.conversion
  "Contains functions for parsing and converting JSON queries into query conditions"
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

(def query-condition-name->condition-type-map
  "A mapping of query condition names to the query condition type based on concept-type."
  {:collection {:entry-title :string
                :entry-id :string
                :provider :string
                :or :or
                :and :and
                :not :not}})

(defn- query-condition-name->condition-type
  "Returns the query condition type based on the given concept-type and param-name."
  [concept-type param-name]
  (get-in query-condition-name->condition-type-map [concept-type param-name]))

(defmulti parse-json-condition
  "Converts a JSON query condition into a query model condition"
  (fn [concept-type condition-name value]
    (query-condition-name->condition-type concept-type condition-name)))

(defmethod parse-json-condition :default
  [concept-type condition-name value]
  (errors/internal-error!
    (format "Could not find parameter handler for [%s] with concept-type [%s]"
            condition-name concept-type)))

(defmethod parse-json-condition :string
  [concept-type condition-name value]
  ;; TODO handle case sensitivity and wildcards
  (qm/string-condition condition-name value false false))

(defn- parse-json-condition-map
  "Parse a JSON condition map into the appropriate query conditions. Conditions within a map are
  implicitly and'ed together."
  [concept-type condition-map]
  (gc/and-conds
    (for [[k v] condition-map]
      (parse-json-condition concept-type k v))))

(defn- grouping-condition
  "Converts :and or :or condition to the appropriate query condition."
  [concept-type values grouping-fn]
  (grouping-fn
    (for [value values]
      (parse-json-condition-map concept-type values))))

;; Example {"and": [{"entry-title": "ET", "provider": "PROV1"}
;;                  {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod parse-json-condition :and
  [concept-type condition-name values]
  (gc/and-conds (map #(parse-json-condition-map concept-type %) values)))

;; Example {"or": [{"entry-title": "ET", "provider": "PROV1"}
;;                 {"revision-date": [null, "2015-04-01T00:00:00Z"]}]}
(defmethod parse-json-condition :or
  [concept-type condition-name values]
  (gc/or-conds (map #(parse-json-condition-map concept-type %) values)))

;; Example - note in maps the conditions are implicitly and'ed together
;;         {"not": {"entry-title": "ET",
;;                  "provider": "PROV1",
;;                  "revision-date": [null, "2015-04-01T00:00:00Z"]}}
(defmethod parse-json-condition :not
  [concept-type condition-name values]
  (gc/and-conds
    (map (fn [[exclude-param exclude-val]]
           (qm/map->NegatedCondition
             {:condition (parse-json-condition concept-type exclude-param exclude-val)}))
         values)))

(defn- json-query->query-condition
  "Converts a JSON query into a query condition."
  [concept-type json-query]
  (let [query-conditions (concat (for [[k v] json-query]
                                   (parse-json-condition concept-type k v)))]
    (when (seq query-conditions) (gc/and-conds query-conditions))))

(defn parse-json-query
  "Converts a JSON query string and query parameters into a query model."
  [concept-type params json-string]
  (let [params (pv/validate-standard-query-parameters concept-type params)
        json-query (u/map-keys->kebab-case (json/parse-string json-string true))]
    (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
                     :concept-type concept-type
                     :condition (json-query->query-condition concept-type json-query)))))

