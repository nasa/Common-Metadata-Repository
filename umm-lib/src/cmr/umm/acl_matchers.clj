(ns cmr.umm.acl-matchers
  "Contains code for determining if a collection matches an acl"
  (:require
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as u]
   [cmr.umm.start-end-date :as sed]
   [cmr.umm.umm-core :as ummc]))

(defn matches-access-value-filter?
  "Returns true if the umm item matches the access-value filter"
  [umm access-value-filter]
  (let [{:keys [min-value max-value include-undefined-value]} access-value-filter]
    (when (and (not min-value) (not max-value) (not include-undefined-value))
      (errors/internal-error!
        "Encountered restriction flag filter where min and max were not set and include-undefined-value was false"))
    (if-let [^double access-value (u/get-real-or-lazy umm :access-value)]
      ;; If there's no range specified then a umm item without a value is restricted
      (when (or min-value max-value)
        (and (or (nil? min-value)
                 (>= access-value ^double min-value))
             (or (nil? max-value)
                 (<= access-value ^double max-value))))
      ;; umm items without a value will only be included if include-undefined-value is true
      include-undefined-value)))

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

(defn matches-temporal-filter?
  "Returns true if the umm item matches the temporal filter"
  [concept-type umm-temporal temporal-filter]
  (when (seq umm-temporal)
    (let [{:keys [start-date stop-date mask]} temporal-filter
          start-date (parse-date start-date)
          stop-date (parse-date stop-date)
          umm-start (parse-date (sed/start-date concept-type umm-temporal))
          umm-end (parse-date (or (sed/end-date concept-type umm-temporal) (tk/now)))]
     (case mask
       "intersect" (t/overlaps? start-date stop-date umm-start umm-end)
       ;; Per ECHO10 API documentation disjoint is the negation of intersects
       "disjoint" (not (t/overlaps? start-date stop-date umm-start umm-end))
       "contains" (time-range1-contains-range2? start-date stop-date umm-start umm-end)))))
