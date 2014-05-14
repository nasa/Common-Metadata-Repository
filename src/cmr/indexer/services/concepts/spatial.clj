(ns cmr.indexer.services.concepts.spatial
  "Contains functions to convert spatial geometry into indexed attributes."
  (:require [cmr.spatial.derived :as d]

            ;; Must be required for derived calculations
            [cmr.spatial.ring :as r]
            [cmr.spatial.polygon :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.lr-binary-search :as lr]
            [cmr.spatial.serialize :as srl]
            [cmr.common.services.errors :as errors]))

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


(defn shape->elastic-doc
  "Converts a spatial shape into the nested elastic attributes"
  [shape coordinate-system]
  ;; ignores coordinate system for now
  (let [shape (d/calculate-derived shape)]
    (merge {:ords (srl/shape->stored-ords shape)}
           (mbr->elastic-attribs "mbr" (srl/shape->mbr shape))
           (mbr->elastic-attribs "lr" (srl/shape->lr shape)))))


(defn spatial->elastic-docs
  "Converts the spatial area of the given catalog item to the elastic documents"
  [coordinate-system catalog-item]
  (when-let [geometries (get-in catalog-item [:spatial-coverage :geometries])]
    (map #(shape->elastic-doc % coordinate-system) geometries)))