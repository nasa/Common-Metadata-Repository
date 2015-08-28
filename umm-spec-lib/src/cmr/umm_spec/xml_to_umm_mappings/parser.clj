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

(defmulti ^:private process-xml-mapping
  "Processes the XML Mapping by using it to extract values from the XPath Context"
  (fn [xpath-context xml-mapping]
    (:type xml-mapping)))

(defmethod process-xml-mapping :object
  [xpath-context {:keys [parse-type properties]}]
  (let [{:keys [constructor-fn]} parse-type
        record-map (util/remove-nil-keys
                     (into {} (for [[prop-name sub-def] properties]
                                [prop-name (process-xml-mapping xpath-context sub-def)])))]
    (when (seq (keys record-map))
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
    (let [elements (seq (:context new-xpath-context))
          values (remove nil?
                         (for [element elements
                               :let [single-item-xpath-context (assoc new-xpath-context
                                                                      :context [element])]]
                           (if (and template (:type template))
                             (process-xml-mapping single-item-xpath-context template)
                             (parse-primitive-value (:parse-type template "string")
                                                    single-item-xpath-context))))]
      (when (seq values) (vec values)))))

(defmethod process-xml-mapping :xpath-with-regex
  [xpath-context {:keys [xpath regex]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath xpath))]
    (let [elements (seq (:context new-xpath-context))]
      (first (for [element elements
                   :let [match (re-matches regex (-> element :content first))]
                   :when match
                   :let [nil-if-empty (fn [s] (if (empty? s) nil s))]]
               ;; The entire string at the xpath is returned if there are no groups in the regular
               ;; expression otherwise the substring corresponding to first group is returned.
               (nil-if-empty (if (string? match) match (second match))))))))

(defmethod process-xml-mapping :constant
  [_ {:keys [value]}]
  value)

(defn parse-xml
  "Parses an XML string with the given mappings into UMM records."
  [root-def xml-string]
  (let [xpath-context (sxp/create-xpath-context-for-xml xml-string)]
    (process-xml-mapping xpath-context root-def)))

(defn first-char-string-matching
  [xpath regex]
  {:type :first-char-string-matching
   :xpath (str xpath "/gco:CharacterString")
   :regex regex})

