(ns cmr.umm.test.generators.granule.temporal
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.granule.temporal-coverage :as tc]))

(def range-date-time
  ;; FIXME - We should not be using sampling inside of a generator.
  ;; the bind function is the way to accomplish this.
  (let [begin (first (gen/sample ext-gen/date-time 1))
        end (t/plus begin (t/days 1))]
    (ext-gen/model-gen c/->RangeDateTime (gen/return begin) (ext-gen/optional (gen/return end)))))

(def temporal-coverage-range
  (ext-gen/model-gen
    tc/temporal-coverage
    (gen/hash-map :range-date-time range-date-time)))

(def temporal-coverage-single
  (ext-gen/model-gen
    tc/temporal-coverage
    (gen/hash-map :single-date-time ext-gen/date-time)))

(def temporal-coverage
  (gen/one-of [temporal-coverage-range
               temporal-coverage-single]))

