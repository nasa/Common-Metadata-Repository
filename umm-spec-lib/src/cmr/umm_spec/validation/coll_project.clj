(ns cmr.umm-spec.validation.coll-project
  "Defines validations for UMM collection project"
  (:require
   [clj-time.core :as time]
   [cmr.common.validations.core :as v]
   [cmr.umm-spec.date-util :as date]
   [cmr.umm-spec.validation.umm-spec-validation-utils :as vu]))

(defn- project-date-validation
  "Validate that project start date is before project end date"
  [field-path value]
  (let [{:keys [StartDate EndDate]} value]
    (when (and StartDate EndDate (time/after? StartDate EndDate))
      {field-path [(format "StartDate [%s] must be no later than EndDate [%s]"
                           (str StartDate) (str EndDate))]})))

(def projects-validation
  [(vu/unique-by-name-validator :ShortName)
   (v/every project-date-validation)])

(def projects-warning-validation
  [(v/every {:StartDate vu/date-in-past-validator})])
