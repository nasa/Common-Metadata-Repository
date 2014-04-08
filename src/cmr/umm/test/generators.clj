(ns cmr.umm.test.generators
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen :refer [optional]]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g]
            [cmr.umm.temporal-coverage :as tc]))

(def short-names
  (ext-gen/string-alpha-numeric 1 85))

(def version-ids
  (ext-gen/string-alpha-numeric 1 80))

(def long-names
  (ext-gen/string-alpha-numeric 1 1024))

(def products
  (ext-gen/model-gen c/->Product short-names long-names version-ids))

(def entry-titles
  (ext-gen/string-alpha-numeric 1 1030))

;; temporal attributes
(def time-types
  (optional (ext-gen/string-alpha-numeric 1 80)))

(def date-types
  (optional (ext-gen/string-alpha-numeric 1 80)))

(def temporal-range-types
  (optional (ext-gen/string-alpha-numeric 1 80)))

(def precision-of-seconds
  (optional gen/s-pos-int))

(def ends-at-present-flag
  (optional gen/boolean))

(def range-date-times
  (let [begin (first (gen/sample (ext-gen/date-time) 1))
        end (t/plus begin (t/days 1))]
    (ext-gen/model-gen c/->RangeDateTime (gen/return begin) (optional (gen/return end)))))

;; periodic-date-time attributes
(def names
  (ext-gen/string-alpha-numeric 1 30))

(def duration-units
  (gen/one-of [(gen/return "DAY") (gen/return "MONTH") (gen/return "YEAR")]))

(def duration-values
  (gen/choose 1 12))

(def period-cycle-duration-units
  (gen/one-of [(gen/return "DAY") (gen/return "MONTH") (gen/return "YEAR")]))

(def period-cycle-duration-values
  (gen/choose 1 12))

(def periodic-date-times
  (let [begin (first (gen/sample (ext-gen/date-time) 1))
        end (t/plus begin (t/days 1))]
    (ext-gen/model-gen
      c/map->PeriodicDateTime
      (gen/hash-map :name names
                    :start-date (gen/return begin)
                    :end-date (gen/return end)
                    :duration-unit duration-units
                    :duration-value duration-values
                    :period-cycle-duration-unit period-cycle-duration-units
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
                  :single-date-times (gen/vector (ext-gen/date-time) 1 3))))

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

(def collections
  (gen/fmap (fn [[entry-title product temporal-coverage]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/->UmmCollection entry-id entry-title product temporal-coverage)))
            (gen/tuple entry-titles products temporal-coverages)))

; Generator for basic collections that only have the bare minimal fields
(def basic-collections
  (gen/fmap (fn [[entry-title product]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/->UmmCollection entry-id entry-title product nil)))
            (gen/tuple entry-titles products)))

;;; granule related
(def granule-urs
  (ext-gen/string-ascii 1 80))

(def coll-refs1
  (ext-gen/model-gen g/collection-ref entry-titles))

(def coll-refs2
  (ext-gen/model-gen g/collection-ref short-names version-ids))

(def coll-refs
  (gen/one-of [coll-refs1 coll-refs2]))

(def granules
  (gen/fmap (fn [[granule-ur coll-ref]]
              (g/->UmmGranule granule-ur coll-ref))
            (gen/tuple granule-urs coll-refs)))
