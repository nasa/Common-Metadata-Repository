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
  "Create condition to filter suggestions based on a user's permissions.
  Only create the :permitted-group-id condition if a user belongs to groups."
  [group-ids]
  (if-not (empty? group-ids)
    (gc/or-conds [public-collections-condition
                  (gc/or-conds
                   ; case insensitive pattern condition to allow searching through
                   ; concatenated group names
                   (map (fn [group-id]
                          (qm/string-condition :permitted-group-ids
                                               (format "*%s*" group-id)
                                               :false
                                               :true))
                        group-ids))])
    public-collections-condition))

(defn- empty-token-with-type
  "AND together the root query, types condition,
  and :contains-public-collections condition."
  [root types]
  (let [types-condition (gc/or-conds (map #(qm/text-condition :type %) types))]
    (gc/and-conds [root types-condition public-collections-condition])))

(defn- empty-token-without-type
  "Most basic condition. AND together root and public-collections-condition."
  [root]
  (gc/and-conds [root public-collections-condition]))

(defn- non-empty-token-with-type
  "AND together token and user-group conditions with root condition."
  [root types group-ids]
  (let [types-and-groups-map (conj (user-groups-condition group-ids)
                                   (map #(qm/text-condition :type %) types))
        types-and-groups-conditions (gc/and-conds [types-and-groups-map])]
    (gc/and-conds [root types-and-groups-conditions])))

(defn- non-empty-token-without-type
  "AND together root condition with user-group conditions."
  [root group-ids]
  (gc/and-conds [root (user-groups-condition group-ids)]))

(defn build-autocomplete-condition
  "Construct root condition, then augment it with permissions and types filters
  where applicable."
  [term types token group-ids]
  (let [root (gc/or-conds [(qm/match :value term)
                           (qm/multi-match :phrase_prefix
                                           ["value" "value._2gram" "value._3gram"]
                                           term)])]
    (cond
      (and (empty? token)(empty? types)) (empty-token-without-type root)
      (and (empty? token) (seq types)) (empty-token-with-type root types)
      (and (seq token) (seq types)) (non-empty-token-with-type root types group-ids)
      (and (seq token) (empty? types)) (non-empty-token-without-type root group-ids))))

(defn fetch-group-ids-for-autocomplete
  "Fetch a distinct set of group-ids for an autocomplete query"
  [acls]
  (let [group-permissions (map :group-permissions acls)
        group-ids (->> group-permissions
                       (mapcat #(map :group-id %))
                       (remove nil?))
        registered (->> group-permissions
                        (mapcat #(map :user-type %))
                        (filter #(= % "registered")))]
    (distinct (into registered group-ids))))

(defn autocomplete
  "Execute elasticsearch query to get autocomplete suggestions"
  [context term types opts token]
  (let [acls (af/get-acls context [:catalog-item])
        group-ids (fetch-group-ids-for-autocomplete acls)
        condition (build-autocomplete-condition term types token group-ids)
        query (qm/query {:concept-type :autocomplete
                         :page-size (:page-size opts)
                         :offset (:offset opts)
                         :condition condition
                         :result-fields [:type :value :fields]})]
    (qe/execute-query context query)))
