(ns cmr.search.data.keywords-to-elastic
  "Contains functions for generating keyword search components for elastic.
  Keyword queries are done as follows:
  1. The keywords string is split on whitespace and lower-cased to generate a list of keywords.
  2. This keyword list is stored in the main query record to make it accessible during the final
  query generation. See step 6 for how this is used.
  3. The keyword list is used to construct a primary query by joining all the keywords with
  spaces.
  4. A query_string query is constructed from the primary query against the :keyword field.
  This query is included in the main query like any other parameter query.
  5. When the main query is converted to an elasticsearch query, if the keywords list is nil,
  then the query generation proceeds as before, resulting in a filtered query with a 'match_all'
  as the primary query and the parameters' conditions converted to elasticsearch queries that are
  embedded in the filter.
  6. If the keyword list is not nil, then a 'function score query' is contructed. This consists
  of two parts, a primary query (which is generated as before) and multiple filter queries.
  The primary query determines which documents are returned. The filters
  are used to compute the relevance for each document matching the primary queries.

  A sample function score query:

  {:function_score {
  :functions [
  {
  :boost_factor 1.4
  :filter {...}
  }
  {
  :boost_factor 1.2
  :filter {...}
  }
  ]
  :query {:filtered {:query {:match-all {}}
  :filter primary-query}}}}

  See http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
  for details on the function score query."
  (:require [clojure.string :as str]))

(def short-name-long-name-boost
  "The boost to apply to the short-name/long-name component of the keyword matching"
  1.4)

(def project-boost
  "The boost to apply to the campaign short name / long name fields"
  1.3)

(def platform-boost
  "The boost to apply to the platform short name / long name"
  1.3)

(def instrument-boost
  "The boost to apply to the instrument short name / long name"
  1.2)

(def sensor-boost
  "The boost to apply to the sensor short name / long name"
  1.2)

(def spatial-keyword-boost
  "The boost to apply to the spatial keyword"
  1.1)

(def science-keywords-boost
  "The boost to apply to the science keyword field"
  1.2)

(def temporal-keyword-boost
  "The boost to apply to the temporal keyword field"
  1.1)

(def elastic-regex-wildcard-chars-re
  "Regex to match wildcard characters that need to be processed for elastic regexp query"
  #"([\?\*])")

(def elastic-regex-special-chars-re
  "Regex to match characters that need to be escaped for an elastic regexp query"
  #"([\.\+\|\{\}\[\]\(\)\"\\\<\>]|&&|\|\|)")

(defn process-keyword
  "Appends a '.' to wildcard symbols (? and *) and escapes characters that are Regex operators
  in elastic"
  [keyword]
  (let [escaped-keyword (str/replace keyword elastic-regex-special-chars-re "\\$1")]
    (str/replace escaped-keyword elastic-regex-wildcard-chars-re ".$1")))

(defn keyword-regexp-filter
  "Create a regexp filter for a given field and keyword (allows wildcards)"
  [field keyword]
  (let [regex (str ".*" (process-keyword keyword) ".*")]
    {:regexp {field regex}}))

(defn keyword-exact-match-filter
  "Create a filter that checks for an exact match on a field (allows wildcards)"
  [field keyword]
  (let [regex (process-keyword keyword)]
    {:regexp {field regex}}))

(defn keywords->name-filter
  "Create a filter for keyword searches that checks for a loose match on one field or an
  exact match on another"
  [regex-field exact-field keywords boost]
  {:boost_factor boost
   :filter {:or [{:and (map (partial keyword-regexp-filter regex-field) keywords)}
                 {:or (map (partial keyword-exact-match-filter exact-field) keywords)}]}})

(defn science-keywords-or-filter
  "Create an or filter containing the science keyword fields related to keyword searches"
  [keywd]
  (let [processed-keyword (process-keyword keywd)]
    (map (fn [field] {:regexp {(keyword (str (name field) ".lowercase")) processed-keyword}})
         [:category :topic :term :variable-level-1 :variable-level-1 :variable-level3])))

(defn keywords->sk-filter
  "Create a filter for keyword searches that checks science keywords"
  [keywords boost]
  {:boost_factor boost
   :filter {:nested {:path :science-keywords
                     :filter {:or (science-keywords-or-filter (str/join " " keywords))}}}})

(defn keywords->boosted-exact-match-filter
  "Create a boosted filter for keyword searches that requires an exact match on the given field"
  [field keywords boost]
  (let [keyword (str/join " " keywords)]
    {:boost_factor boost
     :filter (keyword-exact-match-filter field keyword)}))

(defn keywords->boosted-elastic-filters
  "Create filters with boosting for the function score query used with keyword search"
  [keywords]
  [;; long-name, short-name
   (keywords->name-filter :long-name.lowercase
                          :short-name.lowercase keywords
                          short-name-long-name-boost)
   ;; project (ECHO campaign)
   (keywords->name-filter :project-ln.lowercase :project-sn.lowercase keywords project-boost)
   ;; platform
   (keywords->name-filter :platform-ln.lowercase :platform-sn.lowercase keywords platform-boost)
   ;; instrument
   (keywords->name-filter :instrument-ln.lowercase :instrument-sn.lowercase keywords instrument-boost)
   ;; sensor
   (keywords->name-filter :sensor-ln.lowercase :sensor-sn.lowercase keywords sensor-boost)
   ;; science keywords
   (keywords->sk-filter keywords science-keywords-boost)
   ;; spatial-keyword
   (keywords->boosted-exact-match-filter :spatial-keyword.lowercase keywords spatial-keyword-boost)
   ;; temporal-keyword
   (keywords->boosted-exact-match-filter :temporal-keyword.lowercase keywords temporal-keyword-boost)])

