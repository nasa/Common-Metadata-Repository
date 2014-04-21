(ns cmr.umm.test.generators.collection.temporal
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.temporal-coverage :as tc]))

;; temporal attributes
(def time-types
  ;; FIXME - I'd prefer optional to be used where a field is used. A field is optional. The value
  ;; should always be generated.
  (ext-gen/optional (ext-gen/string-alpha-numeric 1 80)))

(def date-types
  (ext-gen/optional (ext-gen/string-alpha-numeric 1 80)))

(def temporal-range-types
  (ext-gen/optional (ext-gen/string-alpha-numeric 1 80)))

(def precision-of-seconds
  (ext-gen/optional gen/s-pos-int))

(def ends-at-present-flag
  (ext-gen/optional gen/boolean))

(def range-date-times
  ;; FIXME - We should not be using sampling inside of a generator.
  ;; the bind function is the way to accomplish this.
  (let [begin (first (gen/sample ext-gen/date-time 1))
        end (t/plus begin (t/days 1))]
    (ext-gen/model-gen c/->RangeDateTime (gen/return begin) (ext-gen/optional (gen/return end)))))

;; periodic-date-time attributes
(def names
  (ext-gen/string-alpha-numeric 1 30))

(def duration-units
  (gen/elements ["DAY" "MONTH" "YEAR"]))

;; FIXME both duration values and period-cycle-duration values aren't ideal here
;; They only choose a valid that makes sense as a month. Their value should change based on
;; the duration unit being used.

(def duration-values
  (gen/choose 1 12))

(def period-cycle-duration-values
  (gen/choose 1 12))

(def periodic-date-times
  ;; FIXME - We should not be using sampling inside of a generator.
  (let [begin (first (gen/sample ext-gen/date-time 1))
        end (t/plus begin (t/days 1))]
    (ext-gen/model-gen
      c/map->PeriodicDateTime
      (gen/hash-map :name names
                    :start-date (gen/return begin)
                    :end-date (gen/return end)
                    :duration-unit duration-units
                    :duration-value duration-values
                    :period-cycle-duration-unit duration-units
                    :period-cycle-duration-value period-cycle-duration-values))))

(def temporal-coverages-ranges
  (ext-gen/model-gen
    tc/temporal-coverage
    (gen/hash-map :time-type time-types
                  :date-type date-types
                  :temporal-range-type temporal-range-types
                  :precision-of-seconds precision-of-seconds
                  :ends-at-present-flag ends-at-present-flag
                  :range-date-times (gen/vector range-date-times 1 3))))

(def temporal-coverages-singles
  (ext-gen/model-gen
    tc/temporal-coverage
    (gen/hash-map :time-type time-types
                  :date-type date-types
                  :temporal-range-type temporal-range-types
                  :precision-of-seconds precision-of-seconds
                  :ends-at-present-flag ends-at-present-flag
                  :single-date-times (gen/vector ext-gen/date-time 1 3))))

(def temporal-coverages-periodics
  (ext-gen/model-gen
    tc/temporal-coverage
    (gen/hash-map :time-type time-types
                  :date-type date-types
                  :temporal-range-type temporal-range-types
                  :precision-of-seconds precision-of-seconds
                  :ends-at-present-flag ends-at-present-flag
                  :periodic-date-times (gen/vector periodic-date-times 1 3))))

(def temporal-coverages
  (gen/one-of [temporal-coverages-ranges
               temporal-coverages-singles
               temporal-coverages-periodics]))

