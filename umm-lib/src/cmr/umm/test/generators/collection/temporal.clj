(ns cmr.umm.test.generators.collection.temporal
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.collection.temporal :as tc]))

;; temporal attributes
(def time-types
  (ext-gen/string-alpha-numeric 1 10))

(def date-types
  (ext-gen/string-alpha-numeric 1 10))

(def temporal-range-types
  (ext-gen/string-alpha-numeric 1 10))

(def precision-of-seconds
  gen/s-pos-int)

(def ends-at-present-flag
  gen/boolean)

(def range-date-times
  (let [dates-gen (gen/fmap sort (gen/vector ext-gen/date-time 1 2))]
    (gen/fmap (fn [[begin end]]
                (c/->RangeDateTime begin end))
              dates-gen)))

;; periodic-date-time attributes
(def names
  (ext-gen/string-alpha-numeric 1 10))

(def duration-units
  (gen/elements ["DAY" "MONTH" "YEAR"]))

(def durations
  (gen/bind duration-units
            (fn [unit] (case unit
                         "DAY" (gen/tuple (gen/return unit) (gen/choose 1 31))
                         "MONTH" (gen/tuple (gen/return unit) (gen/choose 1 12))
                         "YEAR" (gen/tuple (gen/return unit) (gen/choose 1970 2050))))))

(def periodic-date-times
  (let [dates-gen (gen/fmap sort (gen/vector ext-gen/date-time 2))]
    (gen/fmap (fn [[name duration periodic-duration date-range]]
                (let [[duration-unit duration-value] duration
                      [periodic-duration-unit period-cycle-duration-value] periodic-duration
                      [begin end] date-range]
                  (c/map->PeriodicDateTime
                    {:name name
                     :start-date begin
                     :end-date end
                     :duration-unit duration-unit
                     :duration-value duration-value
                     :period-cycle-duration-unit periodic-duration-unit
                     :period-cycle-duration-value period-cycle-duration-value})))
              (gen/tuple names
                         durations
                         durations
                         dates-gen))))

(def temporals-ranges
  (ext-gen/model-gen
    tc/temporal
    (gen/hash-map :time-type (ext-gen/optional time-types)
                  :date-type (ext-gen/optional date-types)
                  :temporal-range-type (ext-gen/optional temporal-range-types)
                  :precision-of-seconds (ext-gen/optional precision-of-seconds)
                  :ends-at-present-flag (ext-gen/optional ends-at-present-flag)
                  :range-date-times (gen/vector range-date-times 1 3))))

(def temporals-singles
  (ext-gen/model-gen
    tc/temporal
    (gen/hash-map :time-type (ext-gen/optional time-types)
                  :date-type (ext-gen/optional date-types)
                  :temporal-range-type (ext-gen/optional temporal-range-types)
                  :precision-of-seconds (ext-gen/optional precision-of-seconds)
                  :ends-at-present-flag (ext-gen/optional ends-at-present-flag)
                  :single-date-times (gen/vector ext-gen/date-time 1 3))))

(def temporals-periodics
  (ext-gen/model-gen
    tc/temporal
    (gen/hash-map :time-type (ext-gen/optional time-types)
                  :date-type (ext-gen/optional date-types)
                  :temporal-range-type (ext-gen/optional temporal-range-types)
                  :precision-of-seconds (ext-gen/optional precision-of-seconds)
                  :ends-at-present-flag (ext-gen/optional ends-at-present-flag)
                  :periodic-date-times (gen/vector periodic-date-times 1 3))))

(def temporals
  (gen/one-of [temporals-ranges
               temporals-singles
               temporals-periodics]))
