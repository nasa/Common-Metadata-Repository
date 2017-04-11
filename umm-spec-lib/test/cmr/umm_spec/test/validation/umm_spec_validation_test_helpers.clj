(ns cmr.umm-spec.test.validation.umm-spec-validation-test-helpers
  (:require
   [clojure.test :refer :all]
   [cmr.common.date-time-parser :as dtp]
   [cmr.umm-spec.models.umm-common-models :as c]
   [cmr.umm-spec.models.umm-collection-models :as coll]
   [cmr.common.services.errors :as e]
   [cmr.umm-spec.validation.umm-spec-validation-core :as v]))

(defn assert-valid
  "Asserts that the given collection is valid."
  [collection]
  (is (empty? (v/validate-collection collection))))

(defn assert-invalid
  "Asserts that the given umm model is invalid and has the expected error messages.
  field-path is the path within the metadata to the error. expected-errors is a list of string error
  messages."
  [collection field-path expected-errors]
  (is (= [(e/map->PathErrors {:path field-path
                              :errors (vec expected-errors)})]
         (v/validate-collection collection))))

(defn assert-multiple-invalid
  "Asserts there are multiple errors at different paths invalid with the UMM. Expected errors
  should be a list of maps with path and errors."
  [collection expected-errors]
  (is (= (set (map e/map->PathErrors expected-errors))
         (set (v/validate-collection collection)))))

(defn assert-warnings-valid
  "Asserts that the given collection has no warnings."
  [collection]
  (is (empty? (v/validate-collection-warnings collection))))

(defn assert-warnings-invalid
  "Asserts that the given umm model is invalid and has the expected error messages.
  field-path is the path within the metadata to the error. expected-errors is a list of string error
  messages."
  [collection field-path expected-errors]
  (is (= [(e/map->PathErrors {:path field-path
                              :errors (vec expected-errors)})]
         (v/validate-collection-warnings collection))))

(defn assert-warnings-multiple-invalid
  "Asserts there are multiple errors at different paths invalid with the UMM. Expected errors
  should be a list of maps with path and errors."
  [collection expected-errors]
  (is (= (set (map e/map->PathErrors expected-errors))
         (set (v/validate-collection-warnings collection)))))

(defn range-date-time
  "Returns a temporal range map given beginning and end date strings.

  Example: (range-date-time \"1999-12-30T19:00:00Z\" \"1999-12-30T19:00:01Z\")"
  [begin-date-time end-date-time]
  (let [begin-date-time (when begin-date-time (dtp/parse-datetime begin-date-time))
        end-date-time (when end-date-time (dtp/parse-datetime end-date-time))]
    (c/map->RangeDateTimeType
      {:BeginningDateTime begin-date-time
       :EndingDateTime end-date-time})))

(defn coll-with-range-date-times
  "Returns a collection with the given temporal ranges. Ranges should be a vector of vectors."
  ([range-date-times]
   (coll-with-range-date-times range-date-times nil))
  ([range-date-times ends-at-present?]
   (coll/map->UMM-C
    {:TemporalExtents (mapv #(c/map->TemporalExtentType {:RangeDateTimes %
                                                         :EndsAtPresentFlag ends-at-present?})
                            range-date-times)
     :EntryTitle "et"})))
