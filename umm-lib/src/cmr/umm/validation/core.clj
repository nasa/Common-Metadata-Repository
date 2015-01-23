(ns cmr.umm.validation.core
  "Defines validations UMM concept types."
  (:require [cmr.common.validations.core :as v]
            [cmr.umm.validation.utils :as vu]
            [cmr.umm.collection :as c]
            [cmr.umm.granule :as g]
            [cmr.umm.spatial :as umm-s]
            [cmr.spatial.validation :as sv])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule))

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

(def collection-validations
  "Defines validations for collections"
  {:product-specific-attributes [(vu/unique-by-name-validator :name)]
   :projects [(vu/unique-by-name-validator :short-name)]
   :spatial-coverage spatial-coverage-validations})

(def granule-validations
  "Defines validations for granules"
  {})

(def umm-validations
  "A list of validations by type"
  {UmmCollection collection-validations
   UmmGranule granule-validations})

(defn validate
  "Validates the umm record returning a list of error messages appropriate for the given metadata
  format and concept type. Returns an empty sequence if it is valid."
  [metadata-format umm]
  (vu/perform-validation metadata-format umm (umm-validations (type umm))))


(comment

  (validate :echo10
            (c/map->UmmCollection
              {:access-value "f"
               :product-specific-attributes [{:name "foo"}
                                             {:name "foo"}
                                             {:name "bar"}]
               :projects [{:short-name "jason"}
                          {:short-name "jason"}]}))

  (require '[cmr.spatial.mbr :as m])
  (require '[cmr.spatial.point :as p])
  (validate :echo10
            (c/map->UmmCollection
              {:spatial-coverage {:geometries [(m/mbr -180 45 180 46)
                                               (p/point 192 80)]}}))


  (validate :dif :collection (c/map->UmmCollection {}))

  )