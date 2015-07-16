(ns cmr.umm-spec.xml-generation
  "Contains functions for generating XML using XML Mappings and a source clojure record"
  (:require [clojure.data.xml :as x]
            [cmr.umm-spec.simple-xpath :as sxp]))

(defmulti generate-element
  "TODO
  should return nil if no value"
  (fn [xpath-context element-name xml-def]
    (:type xml-def)))

(defmethod generate-element "object"
  [xpath-context element-name {:keys [properties]}]
  (when-let [content (seq (for [[sub-def-name sub-def] properties
                                :let [element (generate-element xpath-context sub-def-name sub-def)]
                                :when element]
                            element))]
    (x/element element-name {} content)))

(defmethod generate-element "mpath"
  [xpath-context element-name {:keys [value]}]
  (when-let [value (->> (sxp/parse-xpath value)
                        (sxp/evaluate xpath-context)
                        :context
                        first)]
    (x/element element-name {} (str value))))

(defmethod generate-element "array"
  [xpath-context element-name {:keys [items-mpath items]}]
  (let [new-xpath-context (sxp/evaluate xpath-context (sxp/parse-xpath items-mpath))]
    (for [data (:context new-xpath-context)
          :let [single-item-xpath-context (assoc new-xpath-context :context [data])]]
      (if items
        (generate-element single-item-xpath-context element-name items)
        (x/element element-name {} (str data))))))

(defn generate-xml
  "TODO"
  [mappings record]
  (let [root-def-name (get-in mappings [:to-xml :root])
        definitions (get-in mappings [:to-xml :definitions])
        root-def (get definitions (keyword root-def-name))]
    ;; TODO using indent-str for readability while testing.
    (x/indent-str
      (generate-element (sxp/create-xpath-context-for-data record) root-def-name root-def))))

