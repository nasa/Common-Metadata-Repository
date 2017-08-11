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
    (def warning-msgs (atom ""))
    (when (and latest-create (not (date-util/is-in-past? latest-create)))
      (swap! warning-msgs str "CREATE date value: [" latest-create "] needs to be in the past. ")) 
    (when (and latest-update (not (date-util/is-in-past? latest-update)))
      (swap! warning-msgs str "UPDATE date value: [" latest-update "] needs to be in the past. "))
    (when (and earliest-review (not (date-util/is-in-future? earliest-review)))
      (swap! warning-msgs str "REVIEW date value: [" earliest-review "] needs to be in the future. "))
    (when (and earliest-delete (not (date-util/is-in-future? earliest-delete)))
      (swap! warning-msgs str "DELETE date value: [" earliest-delete "] needs to be in the future. "))
    (when (and latest-create
               earliest-update
               (time/after? latest-create earliest-update))  
      (swap! warning-msgs str "Earliest UPDATE date value: ["  
                              earliest-update
                              "] needs to be later than the CREATE date value: ["
                              latest-create
                              "]"))  
    (when (and latest-review
                earliest-delete
                (time/after? latest-review earliest-delete))
      (swap! warning-msgs str "Earliest DELETE date value: [" 
                              earliest-delete
                              "] needs to be later than the REVIEW date value: "
                              latest-review
                              "]"))
    {field-path [@warning-msgs]}))


