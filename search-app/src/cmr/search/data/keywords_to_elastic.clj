(ns cmr.search.data.keywords-to-elastic
  "Contains functions for generating keyword search components for elastic.
  Keyword queries are done as follows:
  1. The keywords string is split on whitespace and lower-cased to generate a list of keywords.
  Additional keywords are pulled from additional query fields (see keyword-string-fields in
  cmr.search.services.query-walkers.keywords-extractor) and a separate list is created of those.
  The keyword list is in the format: {:keywords [...] :field-keywords [...]}
  2. This keyword list is stored in the main query record to make it accessible during the final
  query generation. See step 6 for how this is used.
  3. The keyword list is used to construct a primary query by joining all the keywords with
  spaces.
  4. A query_string query is constructed from the primary query against the :keyword field.
  This query is included in the main query like any other parameter query.
  5. When the main query is converted to an elasticsearch query, if the keyword lists are nil,
  then the query generation proceeds as before, resulting in a filtered query with a 'match_all'
  as the primary query and the parameters' conditions converted to elasticsearch queries that are
  embedded in the filter.
  6. If the keyword lists are not nil, then a 'function score query' is contructed. This consists
  of two parts, a primary query (which is generated as before) and multiple filter queries.
  The primary query determines which documents are returned. The filters
  are used to compute the relevance for each document matching the primary queries.
  Relevance filters are created from keywords as well as the field-keywords.
  For example, if data-center is in the search params, we want to apply a boost if we
  find the data center name in the project, etc.

  A sample function score query:

  {:function_score {
  :functions [
  {
  :weight 1.4
  :filter {...}
  }
  {
  :weight 1.2
  :filter {...}
  }
  ]
  :query {:filtered {:query {:match-all {}}
  :filter primary-query}}}}

  See http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html
  for details on the function score query."
  (:require [clojure.string :as str]))

(def default-boost
  "Field boost to use if not provided or not found in the default-boosts list"
  1.0)

(def default-boosts
  "Field boosts to use if not provided."
  {:short-name 1.4
   :entry-id 1.4
   :project 1.3
   :platform 1.3
   :instrument 1.2
   :science-keywords 1.2
   :spatial-keyword 1.1
   :temporal-keyword 1.1
   :version-id 1.0
   :entry-title 1.3
   :provider 1.0
   :two-d-coord-name 1.0
   :processing-level-id 1.0
   :data-center 1.0})

(def elastic-regex-wildcard-chars-re
  "Regex to match wildcard characters that need to be processed for elastic regexp query"
  #"([\?\*])")

(def elastic-regex-special-chars-re
  "Regex to match characters that need to be escaped for an elastic regexp query"
  #"([\.\+\|~\{\}\[\]\(\)\"\\\<\>]|&&|\|\|)")

(defn- process-keyword
  "Appends a '.' to wildcard symbols (? and *) and escapes characters that are Regex operators
  in elastic"
  [keyword]
  (let [escaped-keyword (str/replace keyword elastic-regex-special-chars-re "\\$1")]
    (str/replace escaped-keyword elastic-regex-wildcard-chars-re ".$1")))

(defn- keyword-regexp-filter
  "Create a regexp filter for a given field and keyword (allows wildcards)"
  [field keyword]
  (let [regex (str ".*" (process-keyword keyword) ".*")]
    {:regexp {field regex}}))

(defn- keyword-exact-match-filter
  "Create a filter that checks for an exact match on a field (allows wildcards)"
  [field keyword]
  (let [regex (process-keyword keyword)]
    {:regexp {field regex}}))

(defn- keywords->name-filter
  "Create a filter for keyword searches that checks for a loose match on one field or an
  exact match on another"
  [regex-field exact-field keywords boost]
  {:weight boost
   ;; Should the 'and' below actually be an 'or'? Investigate this as part of CMR-1329
   :filter {:or (concat
                 (when-let [keywords (:keywords keywords)]
                  [{:and (map (partial keyword-regexp-filter regex-field) keywords)}
                   {:or (map (partial keyword-exact-match-filter exact-field) keywords)}])
                 (when-let [field-keywords (:field-keywords keywords)]
                  [{:or (concat
                         (map (partial keyword-regexp-filter regex-field) field-keywords)
                         (map (partial keyword-regexp-filter exact-field) field-keywords))}]))}})

(defn- science-keywords-or-filter
  "Create an or filter containing the science keyword fields related to keyword searches"
  [keywd]
  (let [processed-keyword (process-keyword keywd)]
    (map (fn [field] {:regexp {(keyword (str "science-keywords." (name field) ".lowercase"))
                               processed-keyword}})
         [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3])))

(defn- keywords->sk-filter
  "Create a filter for keyword searches that checks science keywords"
  [keywords boost]
  {:weight boost
   :filter {:nested {:path :science-keywords
                     :filter {:or (concat
                                   (when-let [keywords (:keywords keywords)]
                                    (science-keywords-or-filter (str/join " " keywords)))
                                   (when-let [field-keywords (:field-keywords keywords)]
                                    (mapcat science-keywords-or-filter field-keywords)))}}}})

(defn- keywords->boosted-exact-match-filter
  "Create a boosted filter for keyword searches that requires an exact match on the given field"
  [field keywords boost]
  {:weight boost
   :filter {:or (concat
                 (when-let [keywords (:keywords keywords)]
                  [(keyword-exact-match-filter field (str/join " " keywords))])
                 (when-let [field-keywords (:field-keywords keywords)]
                  [{:or (map (partial keyword-regexp-filter field) field-keywords)}]))}})

(defn get-boost
  "Get the boost value for the given field."
  [specified-boosts field]
  (let [include-defaults? (.equalsIgnoreCase "true" (:include-defaults specified-boosts))
        boosts (if specified-boosts
                 (if include-defaults?
                   (merge default-boosts specified-boosts)
                   specified-boosts)
                 default-boosts)
        boost (get boosts field default-boost)]
    (if (string? boost)
      (Double/parseDouble boost)
      boost)))

(defn keywords->boosted-elastic-filters
  "Create filters with boosting for the function score query used with keyword search"
  [keywords specified-boosts]
  (let [get-boost-fn #(get-boost specified-boosts %)]
    [;; long-name, short-name
     (keywords->name-filter :long-name.lowercase
                            :short-name.lowercase keywords
                            (get-boost-fn :short-name))
     ;; entry-id
     (keywords->boosted-exact-match-filter :entry-id.lowercase keywords (get-boost-fn :entry-id))

     ;; project (ECHO campaign)
     (keywords->name-filter :project-ln.lowercase :project-sn2.lowercase keywords
                            (get-boost-fn :project))
     ;; platform
     (keywords->name-filter :platform-ln.lowercase :platform-sn.lowercase keywords
                            (get-boost-fn :platform))
     ;; instrument
     (keywords->name-filter :instrument-ln.lowercase :instrument-sn.lowercase keywords
                            (get-boost-fn :instrument))
     ;; science keywords
     (keywords->sk-filter keywords (get-boost-fn :science-keywords))
     ;; spatial-keyword
     (keywords->boosted-exact-match-filter :spatial-keyword.lowercase keywords
                                           (get-boost-fn :spatial-keyword))
     ;; temporal-keyword
     (keywords->boosted-exact-match-filter :temporal-keyword.lowercase keywords
                                           (get-boost-fn :temporal-keyword))
     ;; version-id
     (keywords->boosted-exact-match-filter :version-id.lowercase keywords
                                           (get-boost-fn :version-id))

     ;; entry-title
     (keywords->boosted-exact-match-filter :entry-title.lowercase keywords
                                           (get-boost-fn :entry-title))

     ;; doi
     (keywords->boosted-exact-match-filter :doi.lowercase keywords
                                           (get-boost-fn :doi))
     ;; provider-id
     (keywords->boosted-exact-match-filter :provider-id.lowercase keywords
                                           (get-boost-fn :provider))

     ;; two-d-coord-name
     (keywords->boosted-exact-match-filter :two-d-coord-name.lowercase keywords
                                           (get-boost-fn :two-d-coord-name))

     ;; processing-level-id
     (keywords->boosted-exact-match-filter :processing-level-id.lowercase keywords
                                           (get-boost-fn :processing-level-id))

     ;; data-center
     (keywords->boosted-exact-match-filter :data-center.lowercase keywords
                                           (get-boost-fn :data-center))]))
