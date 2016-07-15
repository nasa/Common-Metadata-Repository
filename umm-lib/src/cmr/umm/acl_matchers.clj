(ns cmr.umm.acl-matchers
  "Contains code for determining if a collection matches an acl"
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]
            [clj-time.core :as t]
            [cmr.umm.start-end-date :as sed]
            [cmr.common.time-keeper :as tk]
            [cmr.common.util :as u]
            [cmr.umm.core :as ummc]))

(def ^:private supported-collection-identifier-keys
  #{:entry-titles :access-value :temporal})

(defn- coll-matches-collection-id?
  "Returns true if the collection matches the collection id"
  [coll collection-id]
  (= (:data-set-id collection-id) (:entry-title coll)))

(defn matches-access-value-filter?
  "Returns true if the umm item matches the access-value filter"
  [umm access-value-filter]
  (let [{:keys [min-value max-value include-undefined]} access-value-filter]
    (when (and (not min-value) (not max-value) (not include-undefined))
      (errors/internal-error!
        "Encountered restriction flag filter where min and max were not set and include-undefined was false"))
    (if-let [^double access-value (u/get-real-or-lazy umm :access-value)]
      ;; If there's no range specified then a umm item without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; umm item's without a value will only be included if include-undefined is true
      include-undefined)))

(defn- time-range1-contains-range2?
  "Returns true if the time range1 completely contains range 2. Start and ends are inclusive."
  [start1 end1 start2 end2]
  (let [interval1 (t/interval start1 end1)]
    (and
      ;; Is start2 in the range
      (or (= start1 start2) (= end1 start2) (t/within? interval1 start2))
      ;; Is end2 in the range
      (or (= end1 end2) (= start1 end2) (t/within? interval1 end2)))))

(defn matches-temporal-filter?
  "Returns true if the umm item matches the temporal filter"
  [concept-type umm-temporal temporal-filter]
  (when umm-temporal
    (when-not (= :acquisition (:temporal-field temporal-filter))
      (errors/internal-error!
        (format "Found acl with unsupported temporal filter field [%s]" (:temporal-field temporal-filter))))

    (let [{:keys [start-date end-date mask]} temporal-filter
          umm-start (sed/start-date concept-type umm-temporal)
          umm-end (or (sed/end-date concept-type umm-temporal) (tk/now))]
      (case mask
        :intersect (t/overlaps? start-date end-date umm-start umm-end)
        ;; Per ECHO10 API documentation disjoint is the negation of intersects
        :disjoint (not (t/overlaps? start-date end-date umm-start umm-end))
        :contains (time-range1-contains-range2? start-date end-date umm-start umm-end)))))

(defn coll-matches-collection-identifier?
  "Returns true if the collection matches the collection identifier"
  [coll coll-id]
  (let [coll-entry-title (:entry-title coll)
        {:keys [entry-titles access-value temporal]} coll-id]
    (and (or (empty? entry-titles)
             (some (partial = coll-entry-title) entry-titles))
         (or (nil? access-value)
             (matches-access-value-filter? coll access-value))
         (or (nil? temporal)
             (matches-temporal-filter? :collection (u/get-real-or-lazy coll :temporal) temporal)))))

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

;; Functions for preparing concepts to be passed to functions above... copied here from search

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
      (u/lazy-assoc :access-value (ummc/parse-concept-access-value concept))
      (u/lazy-assoc :temporal (ummc/parse-concept-temporal concept))
      (assoc :entry-title (get-in concept [:extra-fields :entry-title]))))

(defmethod add-acl-enforcement-fields-to-concept :granule
  [concept]
  (-> concept
      (u/lazy-assoc :access-value (ummc/parse-concept-access-value concept))
      (u/lazy-assoc :temporal (ummc/parse-concept-temporal concept))
      (assoc :collection-concept-id (get-in concept [:extra-fields :parent-collection-id]))))

(defn add-acl-enforcement-fields
  "Adds the fields necessary to enforce ACLs to the concepts."
  [concepts]
  (mapv add-acl-enforcement-fields-to-concept concepts))
