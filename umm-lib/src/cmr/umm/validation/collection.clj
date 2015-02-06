(ns cmr.umm.validation.collection
  "Defines validations for UMM collections."
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.umm.validation.utils :as vu]
            [cmr.umm.spatial :as umm-s]
            [cmr.spatial.validation :as sv]
            [cmr.umm.related-url-helper :as ruh]))

(defn set-geometries-spatial-representation
  "Sets the spatial represention from the spatial coverage on the geometries"
  [spatial-coverage]
  (let [{:keys [spatial-representation geometries]} spatial-coverage]
    (assoc spatial-coverage
           :geometries
           (map #(umm-s/set-coordinate-system spatial-representation %) geometries))))

(def spatial-coverage-validations
  "Defines spatial coverage validations for collections."
  (v/pre-validation
    ;; The spatial representation has to be set on the geometries before the conversion because
    ;;polygons etc do not know whether they are geodetic or not.
    set-geometries-spatial-representation
    {:geometries (v/every sv/spatial-validation)}))

(def sensor-validations
  "Defines the sensor validations for collections"
  {:characteristics (vu/unique-by-name-validator :name)})

(def instrument-validations
  "Defines the instrument validations for collections"
  {:sensors [(v/every sensor-validations)
             (vu/unique-by-name-validator :short-name)]
   :characteristics (vu/unique-by-name-validator :name)})

(def platform-validations
  "Defines the platform validations for collections"
  {:instruments [(v/every instrument-validations)
                 (vu/unique-by-name-validator :short-name)]
   :characteristics (vu/unique-by-name-validator :name)})

(defn- range-date-time-validation
  "Defines range-date-time validation"
  [field-path value]
  (let [{:keys [beginning-date-time ending-date-time]} value]
    (when (and beginning-date-time ending-date-time (t/after? beginning-date-time ending-date-time))
      {field-path [(format "BeginningDateTime [%s] must be no later than EndingDateTime [%s]"
                           (str beginning-date-time) (str ending-date-time))]})))

(def online-access-urls-validation
  "Defines online access urls validation for collections."
  (v/pre-validation
    ruh/downloadable-urls
    (vu/unique-by-name-validator :url)))

(defn- collection-association-name
  "Returns the unique name of collection association for reporting purpose"
  [ca]
  (format "(ShortName [%s] & VersionId [%s])" (:short-name ca) (:version-id ca)))

(defn- coordinate-validator
  "Validates coordinate, minimum must be less than the maximum"
  [field-path value]
  (let [{:keys [min-value max-value]} value]
    (when (and min-value max-value (> min-value max-value))
      {field-path [(format "%%s minimum [%s] must be less than the maximum [%s]."
                           (str min-value) (str max-value))]})))

(def two-d-coord-validations
  "Defines the two d coordinate system validations for collections"
  {:coordinate-1 coordinate-validator
   :coordinate-2 coordinate-validator})

(def collection-validations
  "Defines validations for collections"
  {:product-specific-attributes (vu/unique-by-name-validator :name)
   :projects (vu/unique-by-name-validator :short-name)
   :spatial-coverage spatial-coverage-validations
   :platforms [(v/every platform-validations)
               (vu/unique-by-name-validator :short-name)]
   :associated-difs (vu/unique-by-name-validator identity)
   :temporal {:range-date-times (v/every range-date-time-validation)}
   :related-urls online-access-urls-validation
   :two-d-coordinate-systems [(vu/unique-by-name-validator :name)
                              (v/every two-d-coord-validations)]
   :collection-associations (vu/unique-by-name-validator collection-association-name)})


