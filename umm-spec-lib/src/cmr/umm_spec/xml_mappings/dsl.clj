(ns cmr.umm-spec.xml-mappings.dsl


  "TODO")

  ;; TODO this is a DSL for XML generation
  ;; We should probably change the name



;; Dsl
;; [element-name & content-generators]
;; Results of content generators are joined.
;; content-generator types
;; - map? with :type
;;   - :xpath returns a string of the value
;;   - :attribs - specifies attributes of the element.
;;     - value is a map of keywords to other content generators
;;     - if there are multiple they will be merged in order given
;; - string? - treated as constant
;; - vector? - sub element as described by DSL
;; - seq? - expanded into more content generators
;; - for-each - TODO document it



(defn xpath
  "TODO"
  [value]
  {:type :xpath :value value})


(defn for-each
  [xpath template]
  {:type :for-each
   :xpath xpath
   :template template})


