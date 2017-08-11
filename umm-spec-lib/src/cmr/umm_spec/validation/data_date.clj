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
        earliest-delete (date-util/earliest-date-of-type value "DELETE")]
    ;;First check the absolute range.
    (if (or (and latest-create (not (date-util/is-in-past? latest-create)))
            (and latest-update (not (date-util/is-in-past? latest-update)))
            (and earliest-review (not (date-util/is-in-future? earliest-review)))
            (and earliest-delete (not (date-util/is-in-future? earliest-delete))))
      {field-path [(format 
                     (str "Latest CREATE and UPDATE values: [%s] [%s]"
                          " need to be in the past if not nil and"
                          " earliest REVIEW and DELETE values: [%s] [%s]"
                          " need to be in the future if not nil.")
                     latest-create latest-update earliest-review earliest-delete)]}
      ;;Then check the relative range
      (when (or (and latest-create 
                     earliest-update
                     (time/after? latest-create earliest-update))
                (and latest-review                                
                     earliest-delete
                     (time/after? latest-review earliest-delete)))
        {field-path [(format
                       (str "Latest CREATE value: [%s] must be no later than"
                            " earliest UPDATE value: [%s] if both are not nil and"
                            " latest REVIEW value: [%s] must be no later than"
                            " the earliest DELETE value: [%s] if both are not nil.")
                       latest-create earliest-update latest-review earliest-delete)]})))) 

