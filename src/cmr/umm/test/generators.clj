(ns cmr.umm.test.generators
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g]))

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
  (ext-gen/string-alpha-numeric 1 80))

(def date-types
  (ext-gen/string-alpha-numeric 1 80))

(def temporal-range-types
  (ext-gen/string-alpha-numeric 1 80))

(def range-date-times
  (let [begin (first (gen/sample (ext-gen/date-time) 1))
        end (t/plus begin (t/days 1))]
    (ext-gen/model-gen c/->RangeDateTime (gen/return begin) (gen/return end))))

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
    (ext-gen/model-gen c/->PeriodicDateTime
                       names
                       (gen/return begin)
                       (gen/return end)
                       duration-units
                       duration-values
                       period-cycle-duration-units
                       period-cycle-duration-values)))

(def temporal-coverages-ranges
  (ext-gen/model-gen c/->TemporalCoverage
                     time-types
                     date-types
                     temporal-range-types
                     gen/s-pos-int  ;; precision-of-seconds
                     gen/boolean    ;; ends-at-present-flag
                     (gen/vector range-date-times 1 3)
                     (gen/return nil)
                     (gen/return nil)))

(def temporal-coverages-singles
  (ext-gen/model-gen c/->TemporalCoverage
                     time-types
                     date-types
                     temporal-range-types
                     gen/s-pos-int  ;; precision-of-seconds
                     gen/boolean    ;; ends-at-present-flag
                     (gen/return nil)
                     (gen/vector (ext-gen/date-time) 1 3)
                     (gen/return nil)))

(def temporal-coverages-periodics
  (ext-gen/model-gen c/->TemporalCoverage
                     time-types
                     date-types
                     temporal-range-types
                     gen/s-pos-int  ;; precision-of-seconds
                     gen/boolean    ;; ends-at-present-flag
                     (gen/return nil)
                     (gen/return nil)
                     (gen/vector periodic-date-times 1 3)))

(def temporal-coverages
  (gen/one-of [temporal-coverages-ranges
               temporal-coverages-singles
               temporal-coverages-periodics]))

(def collections
  (gen/fmap (fn [[entry-title product temporal-coverage]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/->UmmCollection entry-id entry-title product temporal-coverage)))
            (gen/tuple entry-titles products temporal-coverages)))

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
