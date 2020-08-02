(ns cmr.search.services.autocomplete-service
  "Service for autocomplete functionality"
  (:require
    [cmr.acl.acl-fetcher :as af]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-execution :as qe]
    [cmr.common-app.services.search.query-model :as qm]))

(def public-collections-condition
  (qm/boolean-condition :contains-public-collections true))

(defn- user-groups-condition
  [user-acls]
  (if-not (empty? user-acls)
    (gc/or-conds [public-collections-condition
                  ; group-id filters need to be case-sensitive because there is
                  ; no permitted-group-ids.lowercase field being indexed
                  (qm/string-conditions :permitted-group-ids user-acls true)])
    public-collections-condition))

(defn- empty-token-with-type
  [term types]
  (let [types-condition (gc/or-conds (map (partial qm/text-condition :type) types))]
    (qm/match-filter :value term (gc/and-conds
                                  [public-collections-condition types-condition]))))

(defn- empty-token-without-type
  [term]
  (qm/match-filter :value term public-collections-condition))

(defn- non-empty-token-with-type
  [term types user-acls]
  (let [type-conditions (gc/or-conds (map (partial qm/text-condition :type) types))]
    (qm/match-filter :value term (gc/and-conds
                                  [type-conditions (user-groups-condition user-acls)]))))

(defn- non-empty-token-without-type
  [term user-acls]
  (qm/match-filter :value term (user-groups-condition user-acls)))

(defn- autocomplete-condition
  "Take in terms, types, tokens, and return query condition based on which are present."
  [term types token user-acls]
  (cond
    (and (empty? token)(empty? types)) (empty-token-without-type term)
    (and (empty? token) (seq types)) (empty-token-with-type term types)
    (and (seq token) (seq types)) (non-empty-token-with-type term types user-acls)
    (and (seq token) (empty? types)) (non-empty-token-without-type term user-acls)))

(defn build-autocomplete-condition
  [term types]
  (let [root (gc/or-conds [(qm/match :value term)
                           (qm/multi-match :phrase_prefix
                                           ["value" "value._2gram" "value._3gram"]
                                           term)])]
    (if (empty? types)
      root
      (gc/and-conds [root
                     (gc/or-conds (map #(qm/text-condition :type %) types))]))))

(defn autocomplete
  "Execute elasticsearch query to get autocomplete suggestions"
  [context term types opts token]
  (let [acl-ids (->> (af/get-acls context [:catalog-item])
                     (map :group-permissions)
                     (mapcat #(map :group-id %))
                     (remove nil?))
        condition (autocomplete-condition term types token acl-ids)
        query (qm/query {:concept-type :autocomplete
                         :page-size (:page-size opts)
                         :offset (:offset opts)
                         :condition condition
                         :result-fields [:type :value :fields]})]
    (qe/execute-query context query)))
