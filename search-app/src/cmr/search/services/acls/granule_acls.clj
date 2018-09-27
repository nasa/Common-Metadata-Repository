(ns cmr.search.services.acls.granule-acls
  "Contains functions for manipulating granule acls"
  (:require
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.common.concepts :as c]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as u]
   [cmr.search.models.query :as q]
   [cmr.search.services.acl-service :as acl-service]
   [cmr.search.services.acls.acl-helper :as acl-helper]
   [cmr.search.services.acls.collections-cache :as coll-cache]
   [cmr.search.services.query-walkers.collection-concept-id-extractor :as coll-id-extractor]
   [cmr.search.services.query-walkers.collection-query-resolver :as r]
   [cmr.search.services.query-walkers.provider-id-extractor :as provider-id-extractor]
   [cmr.umm-spec.acl-matchers :as umm-matchers]))

(defmulti filter-applicable-granule-acls
  (fn [context coll-ids-by-prov provider-ids acls]
    (if (empty? coll-ids-by-prov)
      (if (empty? provider-ids)
        :no-query-or-provider-coll-ids
        :with-provider-ids-no-query-ids)
      :with-query-coll-ids)))

;; There are no query collection ids or provider ids so all granule applicable acls are used.
(defmethod filter-applicable-granule-acls :no-query-or-provider-coll-ids
  [context _ _ acls]
  (filter (comp :granule-applicable :catalog-item-identity) acls))

(defn- in?
  "Returns true if collection contains the provided element."
  [coll element]
  (some #(= element %) coll))

;; Limit ACLs to only the ones that affect the matching provider
(defmethod filter-applicable-granule-acls :with-provider-ids-no-query-ids
  [context _ provider-ids acls]
  (->> acls
       (filter #(in? provider-ids (get-in % [:catalog-item-identity :provider-id])))
       (filter (comp :granule-applicable :catalog-item-identity))))

;; The query contains collection concept ids so they will be used to limit which acls are added to
;; the query.
(defmethod filter-applicable-granule-acls :with-query-coll-ids
  [context coll-ids-by-prov _ acls]
  (filter (fn [acl]
            (let [{{:keys [provider-id] :as cii} :catalog-item-identity} acl
                  acl-coll-ids (map string/trim (get-in cii [:collection-identifier :concept-ids]))]
              (and (:granule-applicable cii)
                   ;; applies to a provider the user is searching
                   (coll-ids-by-prov provider-id)
                   (or ; The ACL applies to no specific collection
                       (empty? acl-coll-ids)
                       ;; The acl applies to a specific collection that the user is searching
                       (some (coll-ids-by-prov provider-id) acl-coll-ids)))))
          acls))

(defn- access-value->query-condition
  "Converts an access value filter from an ACL into a query condition."
  [access-value-filter]
  (when-let [{:keys [include-undefined-value min-value max-value]} access-value-filter]
    (let [value-cond (when (or min-value max-value)
                       (cqm/numeric-range-condition :access-value min-value max-value))
          include-undefined-cond (when include-undefined-value
                                   (cqm/->NegatedCondition
                                     (cqm/->ExistCondition :access-value)))]
      (if (and value-cond include-undefined-cond)
        (gc/or-conds [value-cond include-undefined-cond])
        (or value-cond include-undefined-cond)))))

(defn- temporal->query-condition
  "Converts a temporal filter from an ACL into a query condition"
  [temporal-filter]
  (let [{:keys [start-date stop-date mask]} temporal-filter]
    (case mask
      ;; The granule just needs to intersect with the date range.
      "intersect" (q/map->TemporalCondition {:start-date start-date
                                             :end-date stop-date
                                             :exclusive? false})
      ;; Disjoint is intersects negated.
      "disjoint" (cqm/->NegatedCondition (temporal->query-condition
                                          (assoc temporal-filter :mask "intersect")))
      ;; The granules temporal must start and end within the temporal range
      "contains" (gc/and-conds [(cqm/date-range-condition :start-date start-date stop-date false)
                                (cqm/->ExistCondition :end-date)
                                (cqm/date-range-condition :end-date start-date stop-date false)]))))

(defmulti provider->collection-condition
  "Converts a provider id from an ACL into a collection query condition that will find all collections
  in the provider. query-coll-ids should be a list of the collection ids given by the user in their
  original query (or collection ids resolved from collection query conditions) limited to the
  current provider."
  (fn [query-coll-ids provider-id]
    (if (empty? query-coll-ids)
      :no-query-coll-ids
      :with-query-coll-ids)))

(defmethod provider->collection-condition :no-query-coll-ids
  [query-coll-ids provider-id]
  (q/->CollectionQueryCondition
    (cqm/string-condition :provider-id provider-id true false)))

(defmethod provider->collection-condition :with-query-coll-ids
  [query-coll-ids provider-id]
  (cqm/string-conditions :collection-concept-id query-coll-ids true))

(defn concept-ids->collection-condition
  "Helper for converting a list of concept ids from an ACL into a collection condition that will
  match it. query-coll-ids should be a list of collection ids from the query for this particular provider.
  If it is nil it will be considered as the query having no collection ids. An empty set will be
  considered as the query has collection ids but none of them match the provider the concept ids are in."
  [query-coll-ids concept-ids]
  (if query-coll-ids
    (if-let [concept-ids (seq (set/intersection query-coll-ids (set concept-ids)))]
      (cqm/string-conditions :collection-concept-id concept-ids true)
      cqm/match-none)
    (cqm/string-conditions :collection-concept-id concept-ids true)))

(defn collection-identifier->query-condition
  "Converts an acl collection identifier to an query condition. Switches implementations based
  on whether the user's query contained collection ids. This implementation assumes the query-coll-ids
  passed in are limited to the provider of the collection identifier."
  [context query-coll-ids provider-id collection-identifier]
  (let [colls-in-prov-cond (provider->collection-condition query-coll-ids provider-id)]
    (if-let [{:keys [concept-ids access-value temporal]} collection-identifier]
      (let [concept-ids-cond (provider->collection-condition concept-ids provider-id)
            access-value-cond (some-> (access-value->query-condition access-value)
                                      q/->CollectionQueryCondition)
            temporal-cond (some-> temporal temporal->query-condition q/->CollectionQueryCondition)]
        (gc/and-conds (remove nil? [concept-ids-cond
                                    colls-in-prov-cond
                                    access-value-cond
                                    temporal-cond])))
      ;; No other collection info provided so every collection in provider is possible
      colls-in-prov-cond)))

(defn granule-identifier->query-cond
  "Converts an acl granule identifier into a query condition. Returns nil if no granule identifier
  is present."
  [granule-identifier]
  (when-let [{:keys [access-value temporal]} granule-identifier]
    (let [access-value-cond (some-> access-value access-value->query-condition)
          temporal-cond (some-> temporal temporal->query-condition)]
      (if (and access-value-cond temporal-cond)
        (gc/and-conds [access-value-cond temporal-cond])
        (or access-value-cond temporal-cond)))))

(defn acl->query-condition
  "Converts an acl into the equivalent query condition. Ths can return nil if the doesn't grant anything.
  This can happen if it's for one collection that doesn't exist."
  [context coll-ids-by-prov acl]
  (let [{:keys [provider-id collection-identifier granule-identifier]} (:catalog-item-identity acl)
        query-coll-ids (if (seq coll-ids-by-prov)
                         (set (coll-ids-by-prov provider-id))
                         nil)
        collection-cond (collection-identifier->query-condition
                          context query-coll-ids provider-id collection-identifier)
        granule-cond (granule-identifier->query-cond granule-identifier)]
    (if (and collection-cond granule-cond)
      (gc/and-conds [collection-cond granule-cond])
      (or collection-cond granule-cond))))

(defn acls->query-condition
  "Converts a list of acls into a query condition. coll-ids-by-prov should be a map of provider ids
  to collection concept ids from the user's query."
  [context coll-ids-by-prov acls]
  (if (empty? acls)
    cqm/match-none
    (if-let [conds (seq (filter identity
                                (map (partial acl->query-condition context coll-ids-by-prov) acls)))]
      (gc/or-conds conds)
      cqm/match-none)))

;; This expects that collection queries have been resolved before this step.
(defmethod qe/add-acl-conditions-to-query :granule
  [context query]
  (let [coll-ids-by-prov (->> (coll-id-extractor/extract-collection-concept-ids query)
                              ;; Group the concept ids by provider
                              (group-by #(:provider-id (c/parse-concept-id %)))
                              ;; Create a set of concept ids per provider
                              (map (fn [[prov concept-ids]]
                                     [prov (set concept-ids)]))
                              (into {}))
        provider-ids (provider-id-extractor/extract-provider-ids query)
        acls (filter-applicable-granule-acls
               context
               coll-ids-by-prov
               provider-ids
               (acl-helper/get-acls-applicable-to-token context))
        acl-cond (acls->query-condition context coll-ids-by-prov acls)]
    (r/resolve-collection-queries
      context
      (update-in query [:condition] #(gc/and-conds [acl-cond %])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; acls match concept functions

(defn granule-identifier-matches-concept?
  "Returns true if the granule identifier (a field in catalog item identities in ACLs) is nil or it
  matches the concept."
  [gran-identifier concept]
  (let [{:keys [access-value temporal]} gran-identifier]
    (and (if access-value
           (umm-matchers/matches-access-value-filter? :granule concept access-value)
           true)
         (if temporal
           (when-let [umm-temporal (u/lazy-get concept :temporal)]
             (umm-matchers/matches-temporal-filter? :granule umm-temporal temporal))
           true))))

(defn collection-identifier-matches-concept?
  "Returns true if the collection identifier (a field in catalog item identities in ACLs) is nil or
  it matches the concept."
  [context coll-identifier concept]
  (if coll-identifier
    (let [collection-concept-id (:collection-concept-id concept)
          collection (merge {:concept-id collection-concept-id}
                            (coll-cache/get-collection context collection-concept-id))]
      (when-not collection
        (errors/internal-error!
          (format "Collection with id %s was in a granule but was not found using collection cache."
                  collection-concept-id)))
      (umm-matchers/coll-matches-collection-identifier? collection coll-identifier))
    true))

(defn acl-match-concept?
  "Returns true if the acl matches the concept indicating the concept is permitted."
  [context acl concept]
  (let [{provider-id :provider-id
         gran-identifier :granule-identifier
         coll-identifier :collection-identifier} (:catalog-item-identity acl)]
    (and (= provider-id (:provider-id concept))
         (granule-identifier-matches-concept? gran-identifier concept)
         (collection-identifier-matches-concept? context coll-identifier concept))))

(defmethod acl-service/acls-match-concept? :granule
  [context acls concept]
  (some #(acl-match-concept? context % concept) acls))
