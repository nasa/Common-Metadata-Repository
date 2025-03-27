(ns cmr.search.validators.leading-wildcard-validation
  "Implements a validation that checks that there are not too many leading wildcard patterns in a
   query which cause high CPU usage."
  (:require
   [cmr.common.config :refer [defconfig]]
   [cmr.search.services.query-walkers.condition-extractor :as extractor]
   [cmr.common-app.config :as config])
  (:import
   (cmr.common.services.search.query_model StringCondition)))

(defconfig max-number-of-leading-wildcard-patterns
  "Configures the maximum number of leading wildcard patterns that can appear in a single query.
   These are expensive queries in Elasticsearch and can cause high CPU usage if a query contains
   too many"
  {:default 5
   :type Long})

(def ^:private wildcards
  #{\* \?})

(defn- leading-wildcard-pattern-condition?
  "Returns true if a condition is a condition with a leading wildcard pattern"
  [_path condition]
  (and (= (type condition) StringCondition)
       (:pattern? condition)
       (contains? wildcards (first (:value condition)))))

(defn- too-many-leading-wildcard-pattern-message
  "Returns an error message to explain that there are too many wildcard patterns in the query."
  [num-conditions]
  (format
   (str "The query contained [%d] conditions which used a leading wildcard. This is more than the "
        "maximum allowed amount of [%d]. The CMR allows searching with patterns containing the "
        "wildcards * and ?. Patterns which start with a wildcard are expensive to execute so the "
        "number of patterns that can be used in one query is limited. If you would like to find "
        "alternative ways to query please ask the CMR Team at %s. ")
   num-conditions
   (max-number-of-leading-wildcard-patterns)
   (config/cmr-support-email)))

(defn limit-number-of-leading-wildcard-patterns
  "Validates the query doesn't contain too many leading wildcard patterns."
  [query]
  (let [num-conditions (count (extractor/extract-conditions
                               query leading-wildcard-pattern-condition?))]
    (when (> num-conditions (max-number-of-leading-wildcard-patterns))
      [(too-many-leading-wildcard-pattern-message num-conditions)])))
