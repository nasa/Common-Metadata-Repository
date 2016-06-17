(ns cmr.umm-spec.validation.collection
  "Defines validations for UMM collections."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]))

(defn- range-date-time-validation
  "Defines range-date-time validation"
  [field-path value]
  (println value)
  (let [{:keys [BeginningDateTime EndingDateTime]} value]
    (println BeginningDateTime)
    (println EndingDateTime)
    (when (and BeginningDateTime EndingDateTime (t/after? BeginningDateTime EndingDateTime))
      (println (str "Begin: " BeginningDateTime " End: " EndingDateTime))
      {field-path [(format "BeginningDateTime [%s] must be no later than EndingDateTime [%s]"
                           (str BeginningDateTime) (str EndingDateTime))]})))


(defn- temporal-extent-validation
  "Validates list of temporal extent"
  [field-path value]
  (v/validate (v/every range-date-time-validation) (:RangeDateTimes value)))

(def collection-validations
  "Defines validations for collections"
  {:TemporalExtents (v/every temporal-extent-validation)})
