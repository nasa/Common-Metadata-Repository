(ns cmr.search.services.acls.granule-acls
  "Contains functions for manipulating granule acls"
  (:require [cmr.search.models.query :as q]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.services.acl-service :as acl-service]
            [cmr.search.services.acls.acl-helper :as acl-helper]
            [cmr.common.concepts :as c]
            [cmr.search.services.query-walkers.collection-concept-id-extractor :as coll-id-extractor]
            [cmr.search.services.query-walkers.collection-query-resolver :as r]
            [cmr.acl.collection-matchers :as coll-matchers]
            [cmr.search.services.acls.collections-cache :as coll-cache]
            [cmr.common.services.errors :as errors]
            [clojure.set :as set]))

(defmulti filter-applicable-granule-acls
  (fn [context coll-ids-by-prov acls]
    (if (empty? coll-ids-by-prov)
      :no-query-coll-ids
      :with-query-coll-ids)))

;; There are no query collection ids so all granule applicable acls are used.
(defmethod filter-applicable-granule-acls :no-query-coll-ids
  [context _ acls]
  (filter (comp :granule-applicable :catalog-item-identity) acls))

;; The query contains collection concept ids so they will be used to limit which acls are added to
;; the query.
(defmethod filter-applicable-granule-acls :with-query-coll-ids
  [context coll-ids-by-prov acls]

  (filter (fn [acl]
            (let [{{:keys [provider-id] :as cii} :catalog-item-identity} acl
                  entry-titles (get-in cii [:collection-identifier :entry-titles])
                  acl-coll-ids (->> entry-titles
                                    (map (partial coll-cache/get-collection context provider-id))
                                    ;; It's possible an ACL refers to an entry title that doesn't exist
                                    (remove nil?)
                                    (map :concept-id))]
              (and (:granule-applicable cii)
                   ;; applies to a provider the user is searching
                   (coll-ids-by-prov provider-id)
                   (or ; The ACL applies to no specific collection
                       (empty? entry-titles)
                       ;; The acl applies to a specific collection that the user is searching
                       (some (coll-ids-by-prov provider-id) acl-coll-ids)))))
          acls))

(defn access-value->query-condition
  "Converts an access value filter from an ACL into a query condition."
  [access-value-filter]
  (when-let [{:keys [include-undefined min-value max-value]} access-value-filter]
    (let [value-cond (when (or min-value max-value)
                       (q/numeric-range-condition :access-value min-value max-value))
          include-undefined-cond (when include-undefined
                                   (q/->NegatedCondition
                                     (q/->ExistCondition :access-value)))]
      (if (and value-cond include-undefined-cond)
        (gc/or-conds [value-cond include-undefined-cond])
        (or value-cond include-undefined-cond)))))

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
    (q/string-condition :provider-id provider-id true false)))

(defmethod provider->collection-condition :with-query-coll-ids
  [query-coll-ids provider-id]
  (q/string-conditions :collection-concept-id query-coll-ids true))

(defn concept-ids->collection-condition
  "Helper for converting a list of concept ids from an ACL into a collection condition that will
  match it. query-coll-ids should be a list of collection ids from the query for this particular provider.
  If it is nil it will be considered as the query having no collection ids. An empty set will be
  considered as the query has collection ids but none of them match the provider the concept ids are in."
  [query-coll-ids concept-ids]
  (if query-coll-ids
    (if-let [concept-ids (seq (set/intersection query-coll-ids (set concept-ids)))]
      (q/string-conditions :collection-concept-id concept-ids true)
      q/match-none)
    (q/string-conditions :collection-concept-id concept-ids true)))

(defn- provider+entry-titles->collection-condition
  "Converts a provider and entry titles from an ACL into a an equivalent query condition.
  This goes through each entry title and get the equivalent collection and replace it with a
  concept id. If the collection isn't in the cache it adds a collection query condition of all the
  entry titles together. "
  [context query-coll-ids provider-id entry-titles]
  (when (seq entry-titles)
    (let [{:keys [concept-ids entry-titles]}
          (reduce (fn [condition-map entry-title]
                    (if-let [{:keys [concept-id]} (coll-cache/get-collection context provider-id entry-title)]
                      (update-in condition-map [:concept-ids] conj concept-id)
                      (update-in condition-map [:entry-titles] conj entry-title)))
                  {:concept-ids nil
                   :entry-titles nil}
                  entry-titles)
          concept-ids-cond (when concept-ids
                             (concept-ids->collection-condition query-coll-ids concept-ids))
          entry-titles-cond (when entry-titles
                              (q/->CollectionQueryCondition
                                (gc/and-conds [(q/string-condition :provider-id provider-id true false)
                                              (q/string-conditions :entry-title entry-titles true)])))]
      (if (and concept-ids-cond entry-titles-cond)
        (gc/or-conds [concept-ids-cond entry-titles-cond])
        (or concept-ids-cond entry-titles-cond)))))


(defn collection-identifier->query-condition
  "Converts an acl collection identifier to an query condition. Switches implementations based
  on whether the user's query contained collection ids. This implementation assumes the query-coll-ids
  passed in are limited to the provider of the collection identifier."
  [context query-coll-ids provider-id collection-identifier]
  (let [colls-in-prov-cond (provider->collection-condition query-coll-ids provider-id)]
    (if-let [{:keys [entry-titles access-value]} collection-identifier]

      ;; TODO convert rolling temporal to query condition

      (let [entry-titles-cond (provider+entry-titles->collection-condition
                                context query-coll-ids provider-id entry-titles)
            access-value-cond (some-> (access-value->query-condition access-value)
                                      q/->CollectionQueryCondition)]
        (if (and entry-titles-cond access-value-cond)
          (gc/and-conds [entry-titles-cond access-value-cond])
          (or entry-titles-cond
              ;; If there's no entry title condition the access value condition must be scoped
              ;; by provider
              (gc/and-conds [colls-in-prov-cond access-value-cond]))))
      ;; No other collection info provided so every collection in provider is possible
      colls-in-prov-cond)))

(defn granule-identifier->query-cond
  "Converts an acl granule identifier into a query condition."
  [granule-identifier]

  ;; TODO support rolling temporal as a granule query condition

  (some-> granule-identifier
          :access-value
          access-value->query-condition))

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
    q/match-none
    (if-let [conds (seq (filter identity
                                (map (partial acl->query-condition context coll-ids-by-prov) acls)))]
      (gc/or-conds conds)
      q/match-none)))

;; This expects that collection queries have been resolved before this step.
(defmethod acl-service/add-acl-conditions-to-query :granule
  [context query]
  (let [coll-ids-by-prov (->> (coll-id-extractor/extract-collection-concept-ids query)
                              ;; Group the concept ids by provider
                              (group-by #(:provider-id (c/parse-concept-id %)))
                              ;; Create a set of concept ids per provider
                              (map (fn [[prov concept-ids]]
                                     [prov (set concept-ids)]))
                              (into {}))
        acls (filter-applicable-granule-acls
               context
               coll-ids-by-prov
               (acl-helper/get-acls-applicable-to-token context))
        acl-cond (acls->query-condition context coll-ids-by-prov acls)]

    (r/resolve-collection-queries
      context
      (update-in query [:condition] #(gc/and-conds [acl-cond %])))))


(comment

  (def query (cmr.common.dev.capture-reveal/reveal query))
  (def context (cmr.common.dev.capture-reveal/reveal context))

  (acl-service/add-acl-conditions-to-query context query)

)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; acls match concept functions

(defn granule-identifier-matches-concept?
  "Returns true if the granule identifier is nil or it matches the concept."
  [gran-id concept]
  (if-let [{:keys [min-value max-value include-undefined]} (:access-value gran-id)]
    (let [access-value (:access-value concept)]
      (or (and (nil? access-value) include-undefined)
          (and access-value
               (or (and (and min-value max-value)
                        (>= access-value min-value)
                        (<= access-value max-value))
                   (and min-value (nil? max-value) (>= access-value min-value))
                   (and max-value (nil? min-value) (<= access-value max-value))))))
    true))

(defn collection-identifier-matches-concept?
  "Returns true if the collection identifier is nil or it matches the concept."
  [context coll-id concept]
  (if coll-id
    (let [collection-concept-id (:collection-concept-id concept)
          collection (coll-cache/get-collection context collection-concept-id)]
      (when-not collection
        (errors/internal-error!
          (format "Collection with id %s was in a granule but was not found using collection cache."
                  collection-concept-id)))
      (coll-matchers/coll-matches-collection-identifier? collection coll-id))
    true))

(defn acl-match-concept?
  "Returns true if the acl matches the concept indicating the concept is permitted."
  [context acl concept]
  (let [{provider-id :provider-id
         gran-id :granule-identifier
         coll-id :collection-identifier} (:catalog-item-identity acl)]
    (and (= provider-id (:provider-id concept))
         (granule-identifier-matches-concept? gran-id concept)
         (collection-identifier-matches-concept? context coll-id concept))))

(defmethod acl-service/acls-match-concept? :granule
  [context acls concept]
  (some #(acl-match-concept? context % concept) acls))




