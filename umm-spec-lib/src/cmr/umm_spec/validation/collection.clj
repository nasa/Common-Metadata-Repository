(ns cmr.umm-spec.validation.collection
  "Defines validations for UMM collections."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]))

(defn- range-date-time-validation
  "Defines range-date-time validation"
  [field-path value]
  (let [{:keys [BeginningDateTime EndingDateTime]} value]
    (when (and BeginningDateTime EndingDateTime (t/after? BeginningDateTime EndingDateTime))
      {field-path [(format "BeginningDateTime [%s] must be no later than EndingDateTime [%s]"
                           (str BeginningDateTime) (str EndingDateTime))]})))

(def temporal-extent-validation
  {:RangeDateTimes (v/every range-date-time-validation)})

(def collection-validations
  "Defines validations for collections"
  {:TemporalExtents (v/every temporal-extent-validation)})
