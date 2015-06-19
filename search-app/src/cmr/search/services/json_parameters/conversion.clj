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
            [cheshire.core :as json]
            [cmr.umm-spec.specs.umm-c-dsl :as umm-dsl :refer [define-schema define-type]])
  (:import com.github.fge.jsonschema.main.JsonSchemaFactory
           com.github.fge.jackson.JsonLoader))

(defn json-parameters->query
  "Converts parameters into a query model."
  [concept-type params json-query]
  (let [params (pv/validate-aql-or-json-parameters concept-type params)]
    (qm/query (pc/standard-params->query-attribs concept-type params))))

  ; (let [options (u/map-keys->kebab-case (get params :options {}))
  ;       query-attribs (standard-params->query-attribs concept-type params)
  ;       keywords (when (:keyword params)
  ;                  (str/split (str/lower-case (:keyword params)) #" "))
  ;       params (if keywords (assoc params :keyword (str/join " " keywords)) params)
  ;       params (dissoc params :options :page-size :page-num :sort-key :result-format
  ;                      :include-granule-counts :include-has-granules :include-facets
  ;                      :echo-compatible :hierarchical-facets)]
  ;   (if (empty? params)
  ;     ;; matches everything
  ;     (qm/query query-attribs)
  ;     ;; Convert params into conditions
  ;     (let [conditions (map (fn [[param value]]
  ;                             (parameter->condition concept-type param value options))
  ;                           params)]
  ;       (qm/query (assoc query-attribs
  ;                        :condition (gc/and-conds conditions)
  ;                        :keywords keywords))))))



; (defn aql->query
;   "Validates parameters and converts aql into a query model."
;   [params aql]
;   (validate-aql aql)
;   (let [;; remove the DocType from the aql string as clojure.data.xml does not handle it correctly
;         ;; by adding attributes to elements when it is present.
;         xml-struct (x/parse-str (cx/remove-xml-processing-instructions aql))
;         concept-type (get-concept-type xml-struct)
;         params (pv/validate-aql-parameters concept-type params)]
;     (qm/query (assoc (pc/standard-params->query-attribs concept-type params)
;                      :concept-type concept-type
;                      :condition (xml-struct->query-condition concept-type xml-struct)))))

; (defn group-operation
;   "TODO Implement nested grouping"
;   []
;   "TODO")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Testing out JSON Schema
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def string-type {:type :string})

(def schema
  {:$schema "http://json-schema.org/draft-04/schema#"
   ;; All string types, no definitions needed yet
   ; :definitions {}

   :title "JSON Search"
   :description "Used to search for collections in the CMR."
   :type :object
   :properties {:entry-title string-type
                :entry-id string-type
                :provider string-type
                :short-name string-type
                :version string-type}})

; (define-type Query
;   "Defines a JSON search query"
;   {:entry-title string-type
;    :entry-id string-type
;    :provider string-type
;    :short-name string-type
;    :version string-type})

; (define-schema json-query
;   {:main-type Query})

; ;; Create schema
; (def json-schema (json/generate-string json-query {:pretty true}))
(def json-schema (json/generate-string schema {:pretty true}))

(comment
  (println json-schema)
  (validate-query-against-json-schema {:entry-title "ET"
                                       :version "004"
                                       :nonsense 3})
  )


;; Validate query against schema
(defn validate-query-against-json-schema
  [query]
  (let [factory (JsonSchemaFactory/byDefault)
        json-node (JsonLoader/fromString json-schema)
        schema (.getJsonSchema factory json-node)
        query-json-node (JsonLoader/fromString (json/generate-string query))]
; (cmr.common.dev.capture-reveal/capture-all)
    (.validate schema query-json-node)))

;; END Testing JSON Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;