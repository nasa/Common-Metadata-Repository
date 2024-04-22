(ns cmr.umm-spec.acl-matchers
  "Contains code for determining if a collection matches an acl"
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as u]
   [cmr.umm-spec.legacy :as legacy]
   [cmr.umm-spec.time :as umm-time]
   [cmr.umm-spec.umm-spec-core :as umm-spec-core]))

(def ^:private supported-collection-identifier-keys
  #{:entry-titles :access-value :temporal :concept-ids})

(def collection-field-constraints-cache-key
  "The cache key for a URS cache."
  :collection-field-constraints)

(defn matches-concept-access-value-filter?
  [access-value-filter access-value]
  (let [{:keys [min-value max-value include-undefined-value]} access-value-filter]
    (when (and (not min-value) (not max-value) (not include-undefined-value))
      (errors/internal-error!
       "Encountered restriction flag filter where min and max were not set and include-undefined-value was false"))
    ;; We use lazy get here for performance reasons, we do not want to parse the entire concept, we just
    ;; want to pull out this specific value
    (if access-value
      ;; If there's no range specified then a umm item without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; umm items without a value will only be included if include-undefined-value is true
      include-undefined-value)))

(defn matches-access-value-filter?
  "Returns true if the umm item matches the access-value filter"
  [concept-type umm access-value-filter]
  (case concept-type
    :collection (let [^double access-value (:Value (u/get-real-or-lazy umm :AccessConstraints))]
                  (matches-concept-access-value-filter? access-value-filter access-value))
    :granule (let [^double access-value (u/get-real-or-lazy umm :access-value)]
               (matches-concept-access-value-filter? access-value-filter access-value))))

(defn- time-range1-contains-range2?
  "Returns true if the time range1 completely contains range 2. Start and ends are inclusive."
  [start1 end1 start2 end2]
  (let [interval1 (t/interval start1 end1)]
    (and
      ;; Is start2 in the range
      (or (= start1 start2) (= end1 start2) (t/within? interval1 start2))
      ;; Is end2 in the range
      (or (= end1 end2) (= start1 end2) (t/within? interval1 end2)))))

(defn- parse-date
  [date]
  (if (string? date)
    (f/parse (f/formatters :date-time-parser) date)
    date))

(defn matches-concept-temporal-filter?
  "Returns true if the umm item matches the temporal filter"
 [umm-temporal temporal-filter umm-start umm-end]
 (when (seq umm-temporal)
   (let [{:keys [start-date stop-date mask]} temporal-filter
         start-date (parse-date start-date)
         stop-date (parse-date stop-date)]
     (case mask
       "intersect" (t/overlaps? start-date stop-date umm-start umm-end)
       ;; Per ECHO10 API documentation disjoint is the negation of intersects
       "disjoint" (not (t/overlaps? start-date stop-date umm-start umm-end))
       "contains" (time-range1-contains-range2? start-date stop-date umm-start umm-end)))))

(defn matches-temporal-filter?
  "Returns true if the umm item matches the temporal filter"
  [concept-type umm-temporal temporal-filter]
  (when (seq umm-temporal)
    (case concept-type
      :collection (let [coll-with-temporal {:TemporalExtents umm-temporal}
                        umm-start (parse-date (umm-time/collection-start-date coll-with-temporal))
                        umm-end (parse-date (or (umm-time/normalized-end-date coll-with-temporal) (tk/now)))]
                    (matches-concept-temporal-filter? umm-temporal temporal-filter umm-start umm-end))
      :granule (let [umm-start (parse-date (umm-time/granule-start-date umm-temporal))
                     umm-end (parse-date (or (umm-time/granule-end-date umm-temporal) (tk/now)))]
                 (matches-concept-temporal-filter? umm-temporal temporal-filter umm-start umm-end)))))

(defn coll-matches-collection-identifier?
  "Returns true if the collection matches the collection identifier.
   coll is a cached collection
   coll-id is the catalog_item_identity"
  [coll coll-id]
  (let [coll-entry-title (:EntryTitle coll)
        concept-id (or (:concept-id coll)
                       (:id coll))
        {:keys [entry-titles access-value temporal concept-ids]} coll-id]
    (and (or (some (partial = coll-entry-title) entry-titles)
             (some (partial = concept-id) (map name concept-ids))
             (and (empty? concept-ids)
                  (empty? entry-titles)))
         (or (nil? access-value)
             (matches-access-value-filter? :collection coll access-value))
         ;; We use lazy get here for performance reasons, we do not want to parse the entire concept, we just
         ;; want to pull out this specific value
         (or (nil? temporal)
             (matches-temporal-filter? :collection (u/get-real-or-lazy coll :TemporalExtents) temporal)))))

(comment
  ;; these are what the maps look like
  (let [coll {:concept-type :collection
              :provider-id "TCHERRY"
              :EntryTitle "LarcDatasetId-sampleGranule0002"
              :AccessConstraints {:Value 5}
              :TemporalExtents
              [{:RangeDateTimes
                [{:BeginningDateTime "2000-01-01T00:00:00.000Z"
                  :EndingDateTime nil}]}]
              :concept-id "C1200000219-TCHERRY"}
        coll (u/lazy-assoc coll :AccessConstraints {:Value 5})
        coll-id {:entry-titles ["LarcDatasetId-sampleGranule0002"]
                 :access-value {:min-value 0 :max-value 6}
                 :concept-ids ["C1200000219-TCHERRY"]}]
    (println (coll-matches-collection-identifier? coll coll-id)))

  (u/lazy-assoc {} :AccessConstraints {:Value 5}))

(defn- validate-collection-identiier
  "Verifies the collection identifier isn't using any unsupported ACL features."
  [acl collection-identifier]
  (when collection-identifier
      (let [unsupported-keys (set/difference (set (keys collection-identifier))
                                             supported-collection-identifier-keys)]
        (when-not (empty? unsupported-keys)
          (errors/internal-error!
            (format "The ACL with GUID %s had unsupported attributes set: %s"
                    (:id acl)
                    (string/join ", " unsupported-keys)))))))

(defn coll-applicable-acl?
  "Returns true if the acl is applicable to the collection."
  [coll-prov-id coll acl]
  (when-let [{:keys [collection-applicable
                     collection-identifier
                     provider-id]} (:catalog-item-identity acl)]
    (validate-collection-identiier acl collection-identifier)
    (and collection-applicable
         (= coll-prov-id provider-id)
         (or (nil? collection-identifier)
             (coll-matches-collection-identifier? coll collection-identifier)))))

;; Functions for preparing concepts to be passed to functions above.

(defn get-acl-enforcement-collection-fields-fn
  [concept]
  {:AccessConstraints (when-not (= "" (:metadata concept))
                        (umm-spec-core/parse-concept-access-value concept))
   :TemporalExtents (when-not (= "" (:metadata concept))
                      (umm-spec-core/parse-concept-temporal concept))})

(defn get-acl-enforcement-collection-fields
  [context concept]
  (let [concept-id-key (keyword (:concept-id concept))]
    (if-let [cache (cache/context->cache context collection-field-constraints-cache-key)]
      (cache/get-value cache concept-id-key (fn [] (get-acl-enforcement-collection-fields-fn concept)))
      (get-acl-enforcement-collection-fields-fn concept))))

(defn add-acl-enforcement-fields-to-collection
  [context concept]
  (let [concept-map (get-acl-enforcement-collection-fields context concept)]
    (-> concept
        (merge concept-map)
        (assoc :EntryTitle (get-in concept [:extra-fields :entry-title])))))

(defn add-acl-enforcement-fields-to-granule
  [concept]
  (let [deleted? (:deleted concept)]
    (as-> concept concept
          (if-not deleted?
            (u/lazy-assoc concept :access-value (legacy/parse-concept-access-value concept))
            concept)
          (if-not deleted?
            (u/lazy-assoc concept :temporal (legacy/parse-concept-temporal concept))
            concept)
          (assoc concept :collection-concept-id (get-in concept [:extra-fields :parent-collection-id])))))

(defn add-acl-enforcement-fields-to-concept
  "Adds the fields necessary to enforce ACLs to the concept. Temporal and access value are relatively
  expensive to extract so they are lazily associated. The values won't be evaluated until needed."
  [context concept]
  (case (:concept-type concept)
    :collection (add-acl-enforcement-fields-to-collection context concept)
    :granule (add-acl-enforcement-fields-to-granule concept)
    concept))

(defn add-acl-enforcement-fields
  "Adds the fields necessary to enforce ACLs to the concepts."
  [context concepts]
  (mapv add-acl-enforcement-fields-to-concept context concepts))
