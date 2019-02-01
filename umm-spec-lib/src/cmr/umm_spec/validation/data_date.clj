(ns cmr.umm-spec.validation.data-date
  "Defines validations for UMM collection DataDates and MetadataDates"
  (:require
   [clj-time.core :as time]
   [cmr.umm-spec.date-util :as date-util]))

(defn data-dates-warning-validation
  "Validate that DataDates/MetadataDates'
   CREATE, UPDATE, REVIEW and DELETE date values are in the right order and range:
   CREATE needs to be <= UPDATE and they both need to be in the past.
   REVIEW needs to be <= DELETE and they both need to be in the future.
   field-path is :DataDates/:MetadataDates,
   value is the value of :DataDates/:MetadataDates."
  [field-path value]
  (let [latest-create (date-util/latest-date-of-type value "CREATE")
        earliest-update (date-util/earliest-date-of-type value "UPDATE")
        latest-update (date-util/latest-date-of-type value "UPDATE")
        earliest-review (date-util/earliest-date-of-type value "REVIEW")
        latest-review (date-util/latest-date-of-type value "REVIEW")
        earliest-delete (date-util/earliest-date-of-type value "DELETE")
        warning-msgs (concat 
                       (when (and latest-create (not (date-util/is-in-past? latest-create)))
                         [(format "CREATE date value: [%s] should be in the past." latest-create)])
                       (when (and latest-update (not (date-util/is-in-past? latest-update)))
                         [(format "latest UPDATE date value: [%s] should be in the past." latest-update)])
                       (when (and earliest-review (not (date-util/is-in-future? earliest-review)))
                         [(format "earliest REVIEW date value: [%s] should be in the future." earliest-review)])
                       (when (and earliest-delete (not (date-util/is-in-future? earliest-delete)))
                         [(format "DELETE date value: [%s] should be in the future." earliest-delete)])
                       (when (and latest-create earliest-update (time/after? latest-create earliest-update))
                         [(format
                           "Earliest UPDATE date value: [%s] should be equal or later than CREATE date value: [%s]."
                            earliest-update
                            latest-create)])
                       (when (and latest-review earliest-delete (time/after? latest-review earliest-delete))
                         [(format
                           "DELETE date value: [%s] should be equal or later than latest REVIEW date value: [%s]."
                            earliest-delete
                            latest-review)]))]
    (when (seq warning-msgs)
      {field-path warning-msgs})))
