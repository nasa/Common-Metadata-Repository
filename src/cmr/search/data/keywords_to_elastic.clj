(ns cmr.search.data.keywords-to-elastic
"Contains functions for generating keyword search components for elastic.
Keyword queries are done as follows:
 1. The keywords string is split on special characters and whitespace to genrate a list of
    keywords.
 2. This keyword list is stored in the main query record.
 3. The keyword list is used to construct a primary query by joining all the keywords with
    spaces.
 4. A query_string query is constructed from the primary query against the :keyword field.
    This query is included in the main query like any other parameter query.
 5. When the main query is converted to an elasticsearch query, if the keywords list is nil,
    then the query generation proceeds as before, resuing in a filtered query with a 'match_all'
    as the primary query and the paramters conditions converted to elasticsearch queries that are
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

(defn keyword-regexp-filter
  "Create a regexp filter for a given field and keyword"
  [field keyword]
  (let [regex (str ".*" keyword ".*")]
    {:regexp {field regex}}))

(defn keyword-term-filter
  "Create a term filter for a given field/value"
  [field keyword]
  {:term {field keyword}})

(defn keyword-exact-match-filter
  "Create a filter that checks for an exact match"
  [field keyword]
  {:term {:field field
          :value keyword}})

(defn keywords->name-filter
  "Create a filter for keyword searches that checks for a loose match on one field or and
  exact match on another"
  [regex-field exact-field keywords boost]
  {:boost_factor boost
   :filter {:or [{:and (map (partial keyword-regexp-filter regex-field) keywords)}
                 {:or (map (partial keyword-exact-match-filter exact-field) keywords)}]}})

(defn science-keywords-or-filter
  "Create an or filter containing the science keyword fields related to keyword searches"
  [keywd]
  (map (fn [field] {:term {(keyword (str (name field) ".lowercase")) keywd}})
       [:category :topic :term :variable-level-1 :variable-level-1 :variable-level3]))

(defn keywords->sk-filter
  "Create a filter for keyword searches that checks science keywords"
  [keywords boost]
  {:boost_factor boost
   :filter {:nested {:path :science-keywords
                     :filter {:or (science-keywords-or-filter (str/join " " keywords))}}}})

(defn keywords->boosted-term-filter
  "Crete a boosted term filter for keyword searches"
  [field keywords boost]
  (let [keyword (str/join " " keywords)]
    {:boost_factor boost
     :filter (keyword-term-filter field keyword)}))

(defn keywords->boosted-elastic-filters
  "Create filters with boosting for the function score query used with keyword search"
  [keywords]
  [;; entry-title, short-name
   (keywords->name-filter :long-name.lowercase
                          :short-name.lowercase keywords
                          short-name-long-name-boost)
   ;; project (ECHO campaign)
   (keywords->name-filter :project-ln.lowercase :project-sn.lowercase keywords project-boost)
   ;; platform
   (keywords->name-filter :platform-ln.lowercase :platform-sn.lowercase keywords platform-boost)
   ;; TODO - instrument
   ;; need to add long name
   ;; TODO - sensor
   ;; need to add long name
   ;; science keywords
   (keywords->sk-filter keywords science-keywords-boost)
   ;; spatial-keyword
   (keywords->boosted-term-filter :spatial-keywords.lowercase keywords spatial-keyword-boost)
   ;; TODO - temporal-keyword
   ])