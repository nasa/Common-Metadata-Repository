(ns cmr.acl.collection-matchers
  "Contains code for determining if a collection matches an acl"
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [cmr.common.services.errors :as errors]
            [cmr.common.time-keeper :as tk]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [cmr.umm.start-end-date :as sed]))

(def ^:private supported-collection-identifier-keys
  #{:entry-titles :access-value :rolling-temporal})

(defn- coll-matches-collection-id?
  "Returns true if the collection matches the collection id"
  [coll collection-id]
  (= (:data-set-id collection-id) (:entry-title coll)))

(defn- coll-matches-access-value-filter?
  "Returns true if the collection matches the access-value filter"
  [coll access-value-filter]
  (let [{:keys [min-value max-value include-undefined]} access-value-filter]
    (when (and (not min-value) (not max-value) (not include-undefined))
      (errors/internal-error!
        "Encountered restriction flag filter where min and max were not set and include-undefined was false"))
    (if-let [^double access-value (:access-value coll)]
      ;; If there's no range specified then a collection without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; collection's without a value will only be included if include-undefined is true
      include-undefined)))

(defn- time-ranges-intersect?
  "Returns true if the time ranges given by the start and end date times intersect. Start and ends
  are inclusive."
  [start1 end1 start2 end2]
  (let [interval1 (t/interval start1 end1)
        interval2 (t/interval start2 end2)]
    (or (t/within? interval1 start2)
        (t/within? interval1 end2)
        (t/within? interval2 start1)
        (t/within? interval2 end1)
        (= start1 end2)
        (= start1 start2)
        (= end1 end2)
        (= end1 start2))))

(defn- time-range1-contains-range2?
  "Returns true if the time range1 completely contains range 2. Start and ends are inclusive."
  [start1 end1 start2 end2]
  (let [interval1 (t/interval start1 end1)]
    (and
      ;; Is start2 in the range
      (or (= start1 start2) (= end1 start2) (t/within? interval1 start2))
      ;; Is end2 in the range
      (or (= end1 end2) (= start1 end2) (t/within? interval1 end2)))))

(defn- t-minus-millis
  "Returns the time minus the specified number of milliseconds. This had be used because clj-time
  and underlying period can only take Integer instead of Long for milliseconds"
  [date-time millis]
  (c/from-long (- (c/to-long date-time) millis)))

(defn- coll-matches-rolling-temporal?
  "Returns true if the collection matches the rolling-temporal from a collection identifier."
  [coll rolling-temporal]
  (when-let [temporal (:temporal coll)]

    (when-not (= :acquisition (:temporal-field rolling-temporal))
      (errors/internal-error!
        (str "Found acl with unsupported rolling temporal field " (:temporal-field rolling-temporal))))

    (let [{:keys [end duration mask]} rolling-temporal
          now (tk/now)
          rolling-end (t-minus-millis now end)
          rolling-start (t-minus-millis rolling-end duration)
          coll-start (sed/start-date :collection temporal)
          coll-end (or (sed/end-date :collection temporal) now)]

      (case mask
        :intersects (time-ranges-intersect? rolling-start rolling-end coll-start coll-end)
        ;; Per ECHO10 API documentation disjoint is the negation of intersects
        :disjoint (not (time-ranges-intersect? rolling-start rolling-end coll-start coll-end))
        :contains (time-range1-contains-range2? rolling-start rolling-end coll-start coll-end)))))

(defn coll-matches-collection-identifier?
  "Returns true if the collection matches the collection identifier"
  [coll coll-id]
  (let [coll-entry-title (:entry-title coll)
        {:keys [entry-titles access-value rolling-temporal]} coll-id]
    (and (or (empty? entry-titles)
             (some (partial = coll-entry-title) entry-titles))
         (or (nil? access-value)
             (coll-matches-access-value-filter? coll access-value))
         (or (nil? rolling-temporal)
             (coll-matches-rolling-temporal? coll rolling-temporal)))))

(defn coll-applicable-acl?
  "Returns true if the acl is applicable to the collection."
  [coll-prov-id coll acl]
  (when-let [{:keys [collection-applicable
                     collection-identifier
                     provider-id]} (:catalog-item-identity acl)]

    ;; TODO refactor this out into a function
    ;; Verify the collection identifier isn't using any unsupported ACL features.
    (when collection-identifier
      (let [unsupported-keys (set/difference (set (keys collection-identifier))
                                             supported-collection-identifier-keys)]
        (when-not (empty? unsupported-keys)
          (errors/internal-error!
            (format "The ACL with GUID %s had unsupported attributes set: %s"
                    (:id acl)
                    (str/join ", " unsupported-keys))))))

    (and collection-applicable
         (= coll-prov-id provider-id)
         (or (nil? collection-identifier)
             (coll-matches-collection-identifier? coll collection-identifier)))))

