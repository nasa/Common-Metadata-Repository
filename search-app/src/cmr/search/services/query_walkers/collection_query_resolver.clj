(ns cmr.search.services.query-walkers.collection-query-resolver
  "Defines protocols and functions to resolve collection query conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.query-model :as cqm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.elastic-search-index :as idx]
            [cmr.common-app.services.search.complex-to-simple :as c2s]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.set :as set])
  (:import cmr.search.models.query.CollectionQueryCondition))


(defprotocol ResolveCollectionQuery
  "Defines a function to resolve a collection query condition into conditions of
  collection-concept-ids."
  (merge-collection-queries
    [c]
    "Merges together collection query conditions to reduce the number of collection queries.")

  (resolve-collection-query
    [c context]
    "Converts a collection query condition into conditions of collection-concept-ids.
    Returns a list of possible collection ids along with the new condition or the special flag :all.
    All indicates that the results found do not have any impact on matching collections.")

  (is-collection-query-cond?
    [c]
    "Returns true if the condition is a collection query condition"))


(defn- add-collection-ids-to-context
  "Adds the collection ids to the context. Collection ids are put in the context while resolving
  collection queries to reduce the number of collection ids in the query found in subsequently
  processed collection queries."
  [context collection-ids]
  (when (and (not= :all collection-ids) (some nil? collection-ids))
    (errors/internal-error! (str "Nil collection ids in list: " (pr-str collection-ids))))

  (if (or (= :all collection-ids) (nil? collection-ids))
    context
    (update-in context [:collection-ids]
               (fn [existing-ids]
                 (if existing-ids
                   (set/intersection existing-ids collection-ids)
                   collection-ids)))))

(defmulti group-sub-condition-resolver
  "Handles reducing a single condition from a group condition while resolving collection
  queries in a group condition"
  (fn [reduce-info condition]
    (:operation reduce-info)))

(defmethod group-sub-condition-resolver :or
  [reduce-info condition]
  (let [{:keys [context]} reduce-info
        [coll-ids resolved-cond] (resolve-collection-query condition context)
        reduce-info (update-in reduce-info [:resolved-conditions] conj resolved-cond)]
    (if (= coll-ids :all)
      reduce-info
      (update-in reduce-info [:collection-ids] #(if % (set/union % coll-ids) coll-ids)))))

(defmethod group-sub-condition-resolver :and
  [reduce-info condition]
  (let [{:keys [context]} reduce-info
        [coll-ids resolved-cond] (resolve-collection-query condition context)
        context (add-collection-ids-to-context context coll-ids)]
    (-> reduce-info
        (update-in [:resolved-conditions] conj resolved-cond)
        (assoc :context context)
        (assoc :collection-ids (:collection-ids context)))))


(defn- resolve-group-conditions
  "Resolves all the condition from a group condition of the given operation. Returns a tuple
  of collection ids and the group condition containing resolved conditions"
  [operation conditions context]
  (let [{:keys [collection-ids resolved-conditions]}
        (reduce group-sub-condition-resolver
                {:resolved-conditions [] :operation operation :context context}
                conditions)]
    [collection-ids (gc/group-conds operation resolved-conditions)]))

(defn resolve-collection-queries
  [context query]
  (let [query (merge-collection-queries query)]
    (second (resolve-collection-query query context))))

(extend-protocol ResolveCollectionQuery
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query
  (is-collection-query-cond? [_] false)

  (merge-collection-queries
   [query]
   (update-in query [:condition] merge-collection-queries))

  (resolve-collection-query
   [query context]
   [:all (update-in query [:condition] #(second (resolve-collection-query % context)))])

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup
  (is-collection-query-cond? [_] false)

  (merge-collection-queries
   [{:keys [operation conditions]}]
   ;; This is where the real merging happens. Collection queries at the same level in an AND or OR
   ;; can be merged together.
   (let [conditions (map merge-collection-queries conditions)
         {coll-q-conds true others false} (group-by is-collection-query-cond? conditions)]
     (if (seq coll-q-conds)
       (gc/group-conds
        operation
        (concat [(qm/->CollectionQueryCondition
                  (gc/group-conds operation (map :condition coll-q-conds)))]
                others))
       (gc/group-conds operation others))))


  (resolve-collection-query
   [{:keys [operation conditions]} context]
   (if (= :or operation)
     (resolve-group-conditions operation conditions context)
     ;; and operation
     (let [{:keys [coll-id-conds coll-q-conds others]}
           (group-by #(cond
                        (and (= :collection-concept-id (:field %))) :coll-id-conds
                        (is-collection-query-cond? %) :coll-q-conds
                        :else :others)
                     conditions)
           ;; We check if there is only one collection id conditions because this is an AND group.
           ;; The collections we put in the context are OR'd.
           context (if (= 1 (count coll-id-conds))
                     (let [coll-id-cond (first coll-id-conds)
                           collection-ids (cond
                                            (:value coll-id-cond) #{(:value coll-id-cond)}
                                            (:values coll-id-cond) (set (:values coll-id-cond))
                                            :else (errors/internal-error!
                                                   (str "Unexpected collection id cond: "
                                                        (pr-str coll-id-cond))))]
                       (add-collection-ids-to-context context collection-ids))
                     context)]
       (resolve-group-conditions operation (concat coll-id-conds coll-q-conds others) context))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.CollectionQueryCondition
  (merge-collection-queries [this] this)

  (resolve-collection-query
   [{:keys [condition]} context]

   (let [{:keys [collection-ids]} context
         ;; Use collection ids in the context to modify the condition that's executed.
         condition (cond
                     (and collection-ids (empty? collection-ids))
                     ;; The collection ids in the context is an empty set. This query can match
                     ;; nothing.
                     cqm/match-none

                     collection-ids
                     (gc/and-conds [(cqm/string-conditions :concept-id collection-ids true)
                                    condition])

                     :else
                     condition)
         result (idx/execute-query context
                                   (c2s/reduce-query context
                                                     (cqm/query {:concept-type :collection
                                                                 :condition condition
                                                                 :page-size :unlimited})))
         ;; It's possible that many collection concept ids could be found here. If this becomes a
         ;; performance issue we could restrict the collections that are found to ones that we know
         ;; have some granules. The has-granule-results-feature has a cache of collections to
         ;; granule counts. That could be refactored to be usable here.
         collection-concept-ids (map :_id (get-in result [:hits :hits]))]

     (if (empty? collection-concept-ids)
       [#{} cqm/match-none]
       [(set collection-concept-ids)
        (cqm/string-conditions :collection-concept-id collection-concept-ids true)])))

  (is-collection-query-cond? [_] true)

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (merge-collection-queries [this] this)
  (resolve-collection-query [this context] [:all this])
  (is-collection-query-cond? [_] false))
