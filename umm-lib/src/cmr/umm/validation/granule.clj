(ns cmr.umm.validation.granule
  "Defines validations for UMM granules"
  (:require [clj-time.core :as t]
            [cmr.common.validations.core :as v]
            [cmr.umm.validation.utils :as vu]
            [cmr.umm.spatial :as umm-s]
            [cmr.spatial.validation :as sv]
            [cmr.umm.related-url-helper :as ruh]))


(defn set-geometries-spatial-representation
  "Sets the spatial represention from the spatial coverage on the geometries"
  [spatial-coverage]
  (let [{:keys [geometries]} spatial-coverage
        spatial-representation (get-in spatial-coverage [:parent :granule-spatial-representation])]

    (assoc spatial-coverage
           :geometries
           (map #(umm-s/set-coordinate-system spatial-representation %) geometries))))

(def spatial-coverage-validations
  "Defines spatial coverage validations for granules"
  (v/pre-validation
    ;; The spatial representation has to be set on the geometries before the conversion because
    ;;polygons etc do not know whether they are geodetic or not.
    set-geometries-spatial-representation
    {:geometries (v/every sv/spatial-validation)}))

(def granule-validations
  "Defines validations for granules"
  {:spatial-coverage spatial-coverage-validations})


