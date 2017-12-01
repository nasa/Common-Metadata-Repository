(ns cmr.umm-spec.validation.temporal-extent
  "Defines validations for UMM temporal extents."
  (:require
   [clj-time.core :as time]
   [cmr.common.validations.core :as validations-core]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as util]))

(defn- range-date-time-validation
  "Defines range-date-time validation"
  [field-path value]
  (let [{:keys [BeginningDateTime EndingDateTime]} value]
    (when (and BeginningDateTime EndingDateTime (time/after? BeginningDateTime EndingDateTime))
      {field-path [(format "BeginningDateTime [%s] must be no later than EndingDateTime [%s]"
                           (str BeginningDateTime) (str EndingDateTime))]})))

(defn- temporal-end-date-in-past-validator
  "Validate that the end date in TemporalExtents is in the past"
  [field-path value]
  (when (and value (not (date/is-in-past? value)))
    {field-path [(str "Ending date should be in the past. Either set ending date to a date "
                      "in the past or remove end date and set the ends at present flag to true.")]}))

(defn- ends-at-present-validation
  "Validate that the collection ends at present flag and end date time are
  not both set"
  [field-path value]
  (when (and (= true (:EndsAtPresentFlag value))
             (seq (:RangeDateTimes value))
             (not (some nil? (map :EndingDateTime (:RangeDateTimes value)))))
    {field-path [(str "Ends at present flag is set to true, but an ending date is "
                      "specified. Remove the latest ending date or set the ends at "
                      "present flag to false.")]}))

(def temporal-extent-warning-validation
  "Temporal extent validations that will return warnings"
  [ends-at-present-validation
   {:RangeDateTimes (validations-core/every {:BeginningDateTime util/date-in-past-validator
                                             :EndingDateTime temporal-end-date-in-past-validator})
    :SingleDateTimes (validations-core/every util/date-in-past-validator)}])

(def temporal-extent-validation
  "Temporal extent validations that will return errors"
  {:RangeDateTimes (validations-core/every range-date-time-validation)})
