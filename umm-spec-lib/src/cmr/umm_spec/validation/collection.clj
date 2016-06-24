(ns cmr.umm-spec.validation.collection
  "Defines validations for UMM collections."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.validation :as sv]
            [cmr.umm-spec.validation.platform :as p]
            [cmr.umm-spec.validation.additional-attribute :as aa]))

(defn- range-date-time-validation
  "Defines range-date-time validation"
  [field-path value]
  (let [{:keys [BeginningDateTime EndingDateTime]} value]
    (when (and BeginningDateTime EndingDateTime (t/after? BeginningDateTime EndingDateTime))
      {field-path [(format "BeginningDateTime [%s] must be no later than EndingDateTime [%s]"
                           (str BeginningDateTime) (str EndingDateTime))]})))

(def temporal-extent-validation
  {:RangeDateTimes (v/every range-date-time-validation)})

(defn boundary->ring
  [coord-sys boundary]
  (rr/ords->ring coord-sys (mapcat #(vector (:Longitude %) (:Latitude %))(:Points boundary))))

(defn gpolygon->polygon
  [coord-sys gpolygon]
  (poly/polygon
   coord-sys
   (concat [(boundary->ring coord-sys (:Boundary gpolygon))]
           ;; holes would go here
           nil)))

(defn polygon-validation
  [field-path gpolygon]
  (let [coord-sys :geodetic]
    (when-let [errors (seq (sv/validate (gpolygon->polygon coord-sys gpolygon)))]
      {field-path errors})))

(def spatial-extent-validation
  {:HorizontalSpatialDomain
   {:Geometry
    {:GPolygons (v/every polygon-validation)}}})


(def collection-validations
  "Defines validations for collections"
  {:TemporalExtents (v/every temporal-extent-validation)
   :Platforms p/platforms-validation
   :AdditionalAttributes aa/additional-attribute-validation
   :SpatialExtent spatial-extent-validation})
