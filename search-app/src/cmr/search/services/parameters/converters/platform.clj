(ns cmr.search.services.parameters.converters.platform
  "Contains functions for converting hierarchical platforms query parameters to
   conditions. This process is modeled after science_keyword.clj"
  (:require [clojure.string :as string]
            [cmr.common-app.services.search.parameters.converters.nested-field :as nf]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.params :as p]))

;; Converts platforms parameter and values into conditions
(defmethod p/parameter->condition :platforms
  [context concept-type param value options]
  (let [case-sensitive? (p/case-sensitive-field? concept-type param options)
        pattern? (p/pattern-field? concept-type param options)
        group-operation (p/group-operation param options :and)
        target-field (-> param
                         (name)
                         (string/replace #"^platforms" "platforms2") ;; index now lives here
                         (string/replace #"-h$" "-humanized")
                         (keyword))]
    (if (map? (first (vals value)))
      ;; If multiple platforms are passed in like the following
      ;;  -> platforms[0][basis]=foo&platforms[1][basis]=bar
      ;; then this recurses back into this same function to handle each separately
      (gc/group-conds
       group-operation
       (map #(p/parameter->condition context concept-type param % options) (vals value)))
      ;; Creates the platform keyword condition for a group of platform fields and values.
      (nf/parse-nested-condition target-field value case-sensitive? pattern?))))
