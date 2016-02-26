(ns cmr.search.services.query-execution.tags-results-feature
  "This enables the :include-tags feature for collection search results. When it is enabled
  collection search results will include the list of tags that are associated with the collection."
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [clojure.edn :as edn]
            [cmr.common-app.services.search.query-to-elastic :as q2e]
            [cmr.common-app.services.search.query-execution :as query-execution]))

(def stored-tags-field
  "name of the elasticsearch collection mapping field that stores the tags info"
  "tags-gzip-b64")

(defn- escape-wildcards
  [value]
  (-> value
      ;; Escape * and ?
      (str/replace "*" ".*")
      (str/replace "?" ".?")))

(defn- match-patterns?
  "Returns true if the value matches one of the strings that can be parsed as regex."
  [value regex-strs]
  (let [patterns (map #(re-pattern (q2e/escape-query-string (escape-wildcards %))) regex-strs)]
    (when value (some #(re-find % value) patterns))))

(defmethod query-execution/post-process-query-result-feature :tags
  [context query elastic-results query-results feature]
  (let [include-tags (get-in query [:result-options :tags])
        matched-tags (fn [values]
                       (seq (filter #(match-patterns? % include-tags) values)))]
    ;; only keep the tags that matches the include-tags result options
    ;; Note: right now, the :tags is a list of tag-keys,
    ;; the following code needs to change when tags are updated to a list of maps.
    (util/update-in-each query-results
                         [:items]
                         (fn [item]
                           (update item :tags matched-tags)))))

(defn collection-elastic-result->tags
  "Returns the stored tags from collection search elastic-result"
  [result]
  (some-> result
          :fields
          :tags-gzip-b64
          first
          util/gzip-base64->string
          edn/read-string))
