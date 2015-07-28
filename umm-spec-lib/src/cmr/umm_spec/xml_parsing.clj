(ns cmr.umm-spec.xml-parsing
  "TODO"
  (:require [clojure.string :as str]
            [cmr.common.date-time-parser :as dtp]
            [cmr.umm-spec.simple-xpath :as sxp]))

;; TODO the name here doesn't make any sense
(defmulti parse-value
  "TODO"
  ;; TODO this will probably change to a parse context
  (fn [xpath-context umm-def]
    (:type umm-def)))

(defmethod parse-value "object"
  [xpath-context {:keys [parse-type properties]}]
  (let [{:keys [constructor-fn]} parse-type]
    (constructor-fn
      (into {} (for [[prop-name sub-def] properties]
                 [prop-name (parse-value xpath-context sub-def)])))))

(defmulti parse-xml-value
  "TODO"
  (fn [parse-type xpath-context]
    (or (:format parse-type) (:type parse-type))))

(defn extract-xpath-context-value
  [xpath-context]
  (->> xpath-context :context first :content first))

(defmethod parse-xml-value "string"
  [_ xpath-context]
  (extract-xpath-context-value xpath-context))

(defmethod parse-xml-value "date-time"
  [_ xpath-context]
  (dtp/parse-datetime (extract-xpath-context-value xpath-context)))

(defmethod parse-xml-value "integer"
  [parse-type xpath-context]
  (Long/parseLong ^String (extract-xpath-context-value xpath-context)))

(defmethod parse-xml-value "boolean"
  [parse-type xpath-context]
  (= "true" (extract-xpath-context-value xpath-context)))

(defmethod parse-value "xpath"
  [xpath-context {:keys [parse-type value]}]
  ;; TODO XPaths could be parsed ahead of time when loading mappings.
  (parse-xml-value parse-type (sxp/evaluate xpath-context (sxp/parse-xpath value))))

(defmethod parse-value "concat"
  [xpath-context {:keys [parts]}]
  (->> parts
       (mapv #(assoc % :parse-type {:type "string"}))
       (mapv #(parse-value xpath-context %))
       str/join))

(defmethod parse-value "array"
  [xpath-context {:keys [items-xpath items]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath items-xpath))]
    (when-let [elements (seq (:context new-xpath-context))]
      (for [element elements
            :let [single-item-xpath-context (assoc new-xpath-context :context [element])]]
        (if (and items (:type items))
          (parse-value single-item-xpath-context items)
          (parse-xml-value (:parse-type items "string") single-item-xpath-context))))))

(defmethod parse-value "constant"
  [_ {:keys [value]}]
  value)

(defn parse-xml
  "TODO"
  [mappings xml-string]
  (let [xpath-context (sxp/create-xpath-context-for-xml xml-string)
        [root-def-name root-def] (first mappings)]
    (parse-value xpath-context root-def)))

