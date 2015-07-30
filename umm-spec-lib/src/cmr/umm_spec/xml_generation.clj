(ns cmr.umm-spec.xml-generation
  "Contains functions for generating XML using XML Mappings and a source clojure record"
  (:require [clojure.data.xml :as x]
            [cmr.umm-spec.simple-xpath :as sxp]
            [cmr.common.util :as u]))

(defmulti ^:private generate-element
  "Generates an XML element of the given name and definition. The XPath context here is expected to
  be initialized from a source UMM record. nil will be returned if no value can be extracted given the
  current XML definition."
  (fn [xpath-context element-name xml-def]
    (:type xml-def)))

(defn- namespace-map->element-attributes
  "Converts a map of namespaces into the attributes to put on an element."
  [namespaces]
  (u/map-keys (fn [prefix]
                (keyword (str "xmlns:" (name prefix))))
              namespaces))

(defmethod generate-element "object"
  [xpath-context element-name {:keys [properties namespaces]}]
  (when-let [content (seq (for [[sub-def-name sub-def] properties
                                :let [element (generate-element xpath-context sub-def-name sub-def)]
                                :when element]
                            element))]
    (let [attributes (if namespaces
                       (namespace-map->element-attributes namespaces)
                       {})]
      (x/element element-name attributes content))))

(defmethod generate-element "xpath"
  [xpath-context element-name {:keys [value]}]
  (when-let [value (->> (sxp/parse-xpath value)
                        (sxp/evaluate xpath-context)
                        :context
                        first)]
    (x/element element-name {} (str value))))

(defmethod generate-element "array"
  [xpath-context element-name {:keys [items-xpath items]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath items-xpath))]
    (for [data (:context new-xpath-context)
          :let [single-item-xpath-context (assoc new-xpath-context :context [data])]]
      (if items
        (generate-element single-item-xpath-context element-name items)
        (x/element element-name {} (str data))))))

(defn generate-xml
  "Generates XML from a UMM record and the given UMM mappings."
  [mappings record]
  (let [[root-def-name root-def] (first mappings)
        element (generate-element (sxp/create-xpath-context-for-data record) root-def-name root-def)]
    ;; TODO using indent-str for readability while testing.
    (x/indent-str element)))

