(ns cmr.umm-spec.umm-mappings.parser
  "Implements parsing of XML into UMM records."
  (:require [clojure.string :as str]
            [cmr.common.date-time-parser :as dtp]
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
  (dtp/parse-datetime (extract-xpath-context-value xpath-context)))

(defmethod parse-primitive-value "integer"
  [parse-type xpath-context]
  (Long/parseLong ^String (extract-xpath-context-value xpath-context)))

(defmethod parse-primitive-value "boolean"
  [parse-type xpath-context]
  (= "true" (extract-xpath-context-value xpath-context)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; XML Mapping processors

(defmulti ^:private process-xml-mapping
  "Processes the XML Mapping by using it to extract values from the XPath Context"
  (fn [xpath-context xml-mapping]
    (:type xml-mapping)))

(defmethod process-xml-mapping :object
  [xpath-context {:keys [parse-type properties]}]
  (let [{:keys [constructor-fn]} parse-type]
    (constructor-fn
      (into {} (for [[prop-name sub-def] properties]
                 [prop-name (process-xml-mapping xpath-context sub-def)])))))

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
      (for [element elements
            :let [single-item-xpath-context (assoc new-xpath-context :context [element])]]
        (if (and template (:type template))
          (process-xml-mapping single-item-xpath-context template)
          (parse-primitive-value (:parse-type template "string") single-item-xpath-context))))))

(defmethod process-xml-mapping :constant
  [_ {:keys [value]}]
  value)

(defn parse-xml
  "Parses an XML string with the given mappings into UMM records."
  [root-def xml-string]
  (let [xpath-context (sxp/create-xpath-context-for-xml xml-string)]
    (process-xml-mapping xpath-context root-def)))

