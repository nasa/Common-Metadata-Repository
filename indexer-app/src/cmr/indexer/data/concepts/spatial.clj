(ns cmr.indexer.data.concepts.spatial
  "Contains functions to convert spatial geometry into indexed attributes."
  (:require [cmr.spatial.derived :as d]

            ;; Must be required for derived calculations
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.serialize :as srl]
            [cmr.common.services.errors :as errors]
            [cmr.umm.spatial :as umm-s]))

(defn mbr->elastic-attribs
  [prefix mbr]
  (let [with-prefix #(->> %
                          name
                          (str prefix "-")
                          keyword)]
    {(with-prefix :west) (:west mbr)
     (with-prefix :north) (:north mbr)
     (with-prefix :east) (:east mbr)
     (with-prefix :south) (:south mbr)
     (with-prefix :crosses-antimeridian) (mbr/crosses-antimeridian? mbr)}))

(def west-hemisphere
  "an mbr that covers the western hemispher"
  (mbr/mbr -180 90 0 -90))

(def east-hemisphere
  "an mbr that covers the eastern hemisphere"
  (mbr/mbr 0 90 180 -90))

(def special-cases
  "Created for CMR-724. It has mappings of specific spatial areas which cause problems to an equivalent
  representation."
  (let [ords-case-map
        {[-179.9999 0.0, -179.9999 -89.9999, 0.0 -89.9999, 0.0 0.0, 0.0 89.9999, -179.9999 89.9999,
          -179.9999 0.0]
         west-hemisphere

         [-179.9999 -89.9999, 0.0 -89.9999,  0.0 89.9999, -179.9999 89.9999, -179.9999 -89.9999]
         west-hemisphere

         [-179.9999 75, -179.9 75, -179.9 0, -179.9999 0, -179.9999 -89.9999, 0 -89.9999, 0 89.9999,
          -179.9999 89.9999, -179.9999 75]
         west-hemisphere

         [0.0001 -89.9999, 180 -89.9999, 180 89.9999, 0.0001 89.9999, 0.0001 -89.9999]
         east-hemisphere

         [0.0001 -89.9999, 180.0 -89.9999, 180.0 -45.0, 180.0 0.0, 180.0 45.0, 180.0 89.9999,
          0.0001 89.9999, 0.0001 -89.9999]
         east-hemisphere}]
        (into {}
          (for [[ords equiv] ords-case-map]
            [(poly/polygon :geodetic [(apply rr/ords->ring :geodetic ords)])
             equiv]))))


(defn shapes->elastic-doc
  "Converts a spatial shapes into the nested elastic attributes"
  [shapes coordinate-system]
  (let [shapes (->> shapes
                    (mapv (partial umm-s/set-coordinate-system coordinate-system))
                    (mapv #(get special-cases % %))
                    (mapv d/calculate-derived))
        ords-info-map (srl/shapes->ords-info-map shapes)
        lrs (mapv srl/shape->lr shapes)
        ;; union mbrs to get one covering the whole area
        mbr (reduce mbr/union (mapv srl/shape->mbr shapes))
        ;; Choose the largest lr
        lr (->> lrs
                (sort-by mbr/percent-covering-world)
                reverse
                first)]
    (merge ords-info-map
           (mbr->elastic-attribs "mbr" mbr)
           (mbr->elastic-attribs "lr" lr))))


(defn spatial->elastic-docs
  "Converts the spatial area of the given catalog item to the elastic documents"
  [coordinate-system catalog-item]
  (when-let [geometries (get-in catalog-item [:spatial-coverage :geometries])]
    (shapes->elastic-doc geometries coordinate-system)))