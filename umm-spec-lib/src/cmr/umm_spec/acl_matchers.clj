(ns cmr.umm-spec.acl-matchers
  "Contains code for determining if a collection matches an acl"
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as u :refer [update-in-each]]
   [cmr.umm-spec.legacy :as legacy]
   [cmr.umm-spec.time :as umm-time]
   [cmr.umm-spec.umm-spec-core :as umm-spec-core]
   [cmr.umm.acl-matchers :as umm-lib-acl-matchers]))

(def ^:private supported-collection-identifier-keys
  #{:entry-titles :access-value :temporal :concept-ids})

(defmulti matches-access-value-filter?
 "Returns true if the umm item matches the access-value filter"
 (fn [concept-type umm access-value-filter]
   concept-type))

(defmethod matches-access-value-filter? :collection
  [concept-type umm access-value-filter]
  (let [{:keys [min-value max-value include-undefined-value]} access-value-filter]
    (when (and (not min-value) (not max-value) (not include-undefined-value))
      (errors/internal-error!
        "Encountered restriction flag filter where min and max were not set and include-undefined-value was false"))
    ;; We use lazy get here for performance reasons, we do not want to parse the entire concept, we just
    ;; want to pull out this specific value
    (if-let [^double access-value (:Value (u/get-real-or-lazy umm :AccessConstraints))]
      ;; If there's no range specified then a umm item without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; umm items without a value will only be included if include-undefined-value is true
      include-undefined-value)))

(defmethod matches-access-value-filter? :granule
 [concept-type umm access-value-filter]
 (umm-lib-acl-matchers/matches-access-value-filter? umm access-value-filter))

(defn- time-range1-contains-range2?
  "Returns true if the time range1 completely contains range 2. Start and ends are inclusive."
  [start1 end1 start2 end2]
  (let [interval1 (t/interval start1 end1)]
    (and
      ;; Is start2 in the range
      (or (= start1 start2) (= end1 start2) (t/within? interval1 start2))
      ;; Is end2 in the range
      (or (= end1 end2) (= start1 end2) (t/within? interval1 end2)))))

(defmulti matches-temporal-filter?
 "Returns true if the umm item matches the temporal filter"
 (fn [concept-type umm-temporal temporal-filter]
   concept-type))

(defn- parse-date
 [date]
 (if (string? date)
  (f/parse (f/formatters :date-time-parser) date)
  date))

(defmethod matches-temporal-filter? :collection
  [concept-type umm-temporal temporal-filter]
  (when (seq umm-temporal)
    (let [{:keys [start-date stop-date mask]} temporal-filter
          coll-with-temporal {:TemporalExtents umm-temporal}
          start-date (parse-date start-date)
          stop-date (parse-date stop-date)
          umm-start (parse-date (umm-time/collection-start-date coll-with-temporal))
          umm-end (parse-date (or (umm-time/normalized-end-date coll-with-temporal) (tk/now)))]
      (case mask
        "intersect" (t/overlaps? start-date stop-date umm-start umm-end)
        ;; Per ECHO10 API documentation disjoint is the negation of intersects
        "disjoint" (not (t/overlaps? start-date stop-date umm-start umm-end))
        "contains" (time-range1-contains-range2? start-date
                                                 stop-date
                                                 umm-start umm-end)))))

(defmethod matches-temporal-filter? :granule
 [concept-type umm-temporal temporal-filter]
 (umm-lib-acl-matchers/matches-temporal-filter? concept-type umm-temporal temporal-filter))

(defn coll-matches-collection-identifier?
  "Returns true if the collection matches the collection identifier"
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
                    (str/join ", " unsupported-keys)))))))

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

(defn- parse-temporal
 [umm-temporal]
 (for [temporal umm-temporal]
   (-> temporal
       ;; We use lazy assoc here for performance reasons, we do not want to parse the entire concept, we just
       ;; want to pull out these specific values
       (update-in-each [:RangeDateTimes] update :BeginningDateTime #(f/parse (f/formatters :date-time) %))
       (update-in-each [:RangeDateTimes] update :EndingDateTime #(f/parse (f/formatters :date-time) %))
       (update-in-each [:SingleDateTimes] #(f/parse (f/formatters :date-time) %)))))

;; Functions for preparing concepts to be passed to functions above.

(defmulti add-acl-enforcement-fields-to-concept
  "Adds the fields necessary to enforce ACLs to the concept. Temporal and access value are relatively
  expensive to extract so they are lazily associated. The values won't be evaluated until needed."
  (fn [concept]
    (:concept-type concept)))

(defmethod add-acl-enforcement-fields-to-concept :default
  [concept]
  concept)

(defmethod add-acl-enforcement-fields-to-concept :collection
  [concept]
  (-> concept
      (u/lazy-assoc :AccessConstraints (umm-spec-core/parse-concept-access-value concept))
      (u/lazy-assoc :TemporalExtents (umm-spec-core/parse-concept-temporal concept))
      (assoc :EntryTitle (get-in concept [:extra-fields :entry-title]))))

(defmethod add-acl-enforcement-fields-to-concept :granule
  [concept]
  (-> concept
      (u/lazy-assoc :access-value (legacy/parse-concept-access-value concept))
      (u/lazy-assoc :temporal (legacy/parse-concept-temporal concept))
      (assoc :collection-concept-id (get-in concept [:extra-fields :parent-collection-id]))))

(defn add-acl-enforcement-fields
  "Adds the fields necessary to enforce ACLs to the concepts."
  [concepts]
  (mapv add-acl-enforcement-fields-to-concept concepts))
