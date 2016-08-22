(ns cmr.umm.test.generators.granule.temporal
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [clj-time.core :as t]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.granule.temporal :as tc]))

(def range-date-time
  (let [dates-gen (gen/fmap sort (gen/vector ext-gen/date-time 1 2))]
    (gen/fmap (fn [[begin end]]
                (c/->RangeDateTime begin end))
              dates-gen)))

(def temporal-range
  (ext-gen/model-gen
    tc/temporal
    (gen/hash-map :range-date-time range-date-time)))

(def temporal-single
  (ext-gen/model-gen
    tc/temporal
    (gen/hash-map :single-date-time ext-gen/date-time)))

(def temporal
  (gen/one-of [temporal-range
               temporal-single]))

