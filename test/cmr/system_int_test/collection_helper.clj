(ns cmr.system-int-test.collection-helper
  "Provides function to generate UMM collection with generated values and use the given values if provided."
  (:require [cmr.umm.test.generators :as test-gen]
            [clojure.test.check.generators :as gen]
            [cmr.umm.collection :as c]
            [cmr.common.date-time-parser :as p]
            [cmr.umm.temporal-coverage :as tc]))

(defn- fill-in-value
  "Fill the value if present in the given field of the collection, returns the collection"
  [collection field value]
  (if value
    (assoc collection field value)
    collection))

(defn- fill-in-product-value
  "Fill the value if present in the given product field of the collection, returns the collection"
  [collection field value]
  (if value
    (assoc-in collection [:product field] value)
    collection))

(defn- temporal-coverage
  "Return a temporal-coverage with range date time of the given date times"
  [beginning-date-time ending-date-time]
  (let [begin (when beginning-date-time (p/string->datetime beginning-date-time))
        end (when ending-date-time (p/string->datetime ending-date-time))]
    (when (or begin end)
      (tc/temporal-coverage {:range-date-times [(c/->RangeDateTime begin end)]}))))

(defn collection
  "Returns a generated collection with the given values,
  fields with no given values will be filled in with generated ones.
  The date time values should be in UTC date time string format, e.g. 1986-10-14T04:03:27Z"
  [values]
  (let [{:keys [entry-title short-name version-id long-name
                beginning-date-time ending-date-time]} values
        temporal-coverage (temporal-coverage beginning-date-time ending-date-time)]
        (-> (first (gen/sample test-gen/basic-collections 1))
            (fill-in-value :entry-title entry-title)
            (fill-in-product-value :short-name short-name)
            (fill-in-product-value :version-id version-id)
            (fill-in-product-value :long-name long-name)
            (fill-in-value :temporal-coverage temporal-coverage))))
