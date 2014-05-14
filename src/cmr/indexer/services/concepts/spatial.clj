(ns cmr.indexer.services.concepts.spatial
  "Contains functions to convert spatial geometry into indexed attributes."
  (:require [cmr.spatial.derived :as d]

            ;; Must be required for derived calculations
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.lr-binary-search :as lr]
            [cmr.spatial.serialize :as serialize]
            [cmr.common.services.errors :as errors]))


(defprotocol ShapeToElasticAttribs
  (shape->elastic-doc
    [shape coordinate-system]
    "Converts a spatial shape into the nested elastic attributes"))

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

(extend-protocol ShapeToElasticAttribs
  cmr.spatial.polygon.Polygon
  (shape->elastic-doc
    [polygon coordinate-system]

    ;; Only works on geodetic for now

    (let [polygon (d/calculate-derived polygon)
          {:keys [mbr rings]} polygon
          lr (lr/find-lr (first rings))]
      (when-not lr
        (errors/internal-error!
          (format
            "Unable to find lr of ring [%s]. The current LR algorithm is limited and needs to be improved."
            (pr-str (first rings)))))
      (merge {:ords (serialize/shape->stored-ords polygon)}
             (mbr->elastic-attribs :mbr mbr)
             (mbr->elastic-attribs :lr lr)))))

(defn spatial->elastic-docs
  "Converts the spatial area of the given catalog item to the elastic documents"
  [coordinate-system catalog-item]
  (when-let [geometries (get-in catalog-item [:spatial-coverage :geometries])]
    (map #(shape->elastic-doc % coordinate-system) geometries)))