(ns cmr.umm-spec.xml-to-umm-mappings.parser
  "Implements parsing of XML into UMM records."
  (:require [clojure.string :as str]
            [cmr.common.date-time-parser :as dtp]
            [cmr.common.util :as util]
            [cmr.umm-spec.simple-xpath :as sxp]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Primitive value parsing

(defmulti ^:private parse-primitive-value
  "Parses a primitive value from the current XPath context"
  (fn [parse-type xpath-context]
    (or (:format parse-type) (:type parse-type))))

(defn- extract-xpath-context-value
  [xpath-context]
  (->> xpath-context :context first :content first))

(defmethod parse-primitive-value "string"
  [_ xpath-context]
  (extract-xpath-context-value xpath-context))

(defmethod parse-primitive-value "date-time"
  [_ xpath-context]
  (when-let [value (extract-xpath-context-value xpath-context)]
    (dtp/parse-datetime value)))

(defmethod parse-primitive-value "integer"
  [parse-type xpath-context]
  (when-let [value (extract-xpath-context-value xpath-context)]
    (Long/parseLong ^String value)))

(defmethod parse-primitive-value "boolean"
  [parse-type xpath-context]
  (when-let [value (extract-xpath-context-value xpath-context)]
    (= "true" value)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Mapping processors

(defn mapping-type
  "Returns a value for process-xml-mapping to dispatch on."
  [xml-mapping]
  (if (fn? xml-mapping)
    :fn
    (:type xml-mapping)))

(defmulti ^:private process-xml-mapping
  "Processes the XML Mapping by using it to extract values from the XPath Context"
  (fn [xpath-context xml-mapping]
    (mapping-type xml-mapping)))

(defmethod process-xml-mapping :object
  [xpath-context {:keys [parse-type properties]}]
  (let [{:keys [constructor-fn]} parse-type
        record-map (util/remove-nil-keys
                     (into {} (for [[prop-name sub-def] properties]
                                [prop-name (process-xml-mapping xpath-context sub-def)])))]
    (when (seq record-map)
      (constructor-fn record-map))))

(defmethod process-xml-mapping :xpath
  [xpath-context {:keys [parse-type value]}]
  ;; XPaths could be parsed ahead of time when loading mappings to improve performance.
  (parse-primitive-value parse-type (sxp/evaluate xpath-context (sxp/parse-xpath value))))

(defmethod process-xml-mapping :concat
  [xpath-context {:keys [parts]}]
  (->> parts
       (mapv #(assoc % :parse-type {:type "string"}))
       (mapv #(process-xml-mapping xpath-context %))
       str/join))

(defmethod process-xml-mapping :for-each
  [xpath-context {:keys [xpath template]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath xpath))]
    (when-let [elements (seq (:context new-xpath-context))]
      (vec (for [element elements
                 :let [single-item-xpath-context (assoc new-xpath-context :context [element])]]
             (if (and template (mapping-type template))
               (process-xml-mapping single-item-xpath-context template)
               (parse-primitive-value (:parse-type template "string") single-item-xpath-context)))))))

(defmethod process-xml-mapping :xpath-with-regex
  [xpath-context {:keys [xpath regex]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath xpath))]
    (let [elements (seq (:context new-xpath-context))]
      (first (for [element elements
                   :let [match (re-matches regex (-> element :content first))]
                   :when match
                   :let [nil-if-empty (fn [s] (if (empty? s) nil s))]]
               ;; A string response implies there is no group in the regular expression and the
               ;; entire matching string is returned and if there is a group in the regular
               ;; expression, the first group of the matching string is returned.
               (nil-if-empty (if (string? match) match (second match))))))))

(defmethod process-xml-mapping :constant
  [_ {:keys [value]}]
  value)

(defmethod process-xml-mapping :fn
  [xpath-context f]
  (f xpath-context))

(defn parse-xml
  "Parses an XML string with the given mappings into UMM records."
  [root-def xml-string]
  (let [xpath-context (sxp/create-xpath-context-for-xml xml-string)]
    (process-xml-mapping xpath-context root-def)))

