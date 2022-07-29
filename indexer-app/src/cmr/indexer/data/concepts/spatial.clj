(ns cmr.indexer.data.concepts.spatial
  "Contains functions to convert spatial geometry into indexed attributes."
  (:require
   [camel-snake-kebab.core :as csk]
   [cmr.spatial.derived :as d]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.serialize :as srl]
   [cmr.umm-spec.migration.version.collection :as version-collection]
   [cmr.umm-spec.spatial-conversion :as sc]
   [cmr.umm.umm-spatial :as umm-s])
  ;; Must be required for derived calculations
  (:require
   cmr.spatial.geodetic-ring))

(defn mbr->elastic-attribs
  [prefix {:keys [west north east south]} crosses-antimeridian?]
  (let [with-prefix #(->> %
                          name
                          (str prefix "-")
                          keyword)]
    {(with-prefix :west) west
     (with-prefix :north) north
     (with-prefix :east) east
     (with-prefix :south) south
     (with-prefix :west-doc-values) west
     (with-prefix :north-doc-values) north
     (with-prefix :east-doc-values) east
     (with-prefix :south-doc-values) south
     (with-prefix :crosses-antimeridian) crosses-antimeridian?}))

(def west-hemisphere
  "an mbr that covers the western hemisphere"
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
           [(poly/polygon :geodetic [(rr/ords->ring :geodetic ords)])
            equiv]))))

(defn shapes->elastic-doc
  "Converts a spatial shapes into the nested elastic attributes"
  [shapes coordinate-system]
  (let [shapes (->> shapes
                    (mapv (partial umm-s/set-coordinate-system coordinate-system))
                    (mapv #(get special-cases % %))
                    (mapv d/calculate-derived))
        ords-info-map (srl/shapes->ords-info-map shapes)
        lrs (seq (remove nil? (mapv srl/shape->lr shapes)))
        ;; union mbrs to get one covering the whole area
        mbr (reduce mbr/union (mapv srl/shape->mbr shapes))
        ;; Choose the largest lr
        lr (when lrs
             (->> lrs
                  (sort-by mbr/percent-covering-world)
                  reverse
                  first))
        lr-info-map (when lr
                      (mbr->elastic-attribs
                        "lr"  (mbr/round-to-float-map lr false) (mbr/crosses-antimeridian? lr)))]  
    (merge ords-info-map
           ;; The bounding rectangles are converted from double to float for storage in Elasticsearch
           ;; This takes up less space in the fielddata cache when using a numeric range filter with
           ;; fielddata execution mode. During conversion from double to float any loss in precision
           ;; is handled by making the mbr slightly larger than it was and the lr slightly smaller.
           ;; A slightly larger MBR will match a few more items but will still be accurate due to
           ;; the spatial search plugin. A slightly smaller LR will match fewer granules but this
           ;; won't hurt query performance much or accuracy at all.
           ;; This is all a little excessive as the accurracy loss from double to float should be in
           ;; the area of 24 centimeters.
           (mbr->elastic-attribs
             "mbr" (mbr/round-to-float-map mbr true) (mbr/crosses-antimeridian? mbr))
           lr-info-map)))

(defn get-collection-coordinate-system
  "Returns the coordinate system in lowercase keyword for the given collection"
  [collection]
  (when-let [coordinate-system (get-in collection [:SpatialExtent :HorizontalSpatialDomain
                                                   :Geometry :CoordinateSystem])]
    (csk/->kebab-case-keyword coordinate-system)))

(defn get-collection-geometry-shapes
  "Returns the horizontal spatital geometry shapes of the given collection"
  [collection]
  (when-let [geometry (get-in collection [:SpatialExtent :HorizontalSpatialDomain :Geometry])]
    (let [coord-sys (get-collection-coordinate-system collection)
          {points :Points brs :BoundingRectangles gpolygons :GPolygons lines :Lines} geometry]
      (concat
       (map sc/umm-spec-point->point points)
       (map sc/umm-spec-br->mbr brs)
       (map #(sc/gpolygon->polygon coord-sys %) gpolygons)
       (map #(sc/umm-spec-line->line coord-sys %) lines)))))

(defn collection-orbit-parameters->elastic-docs
  "Converts the orbit parameters of the given collection to the elastic documents"
  [collection]
  (when-let [orbit-params (get-in collection [:SpatialExtent :OrbitParameters])]
    {:swath-width (version-collection/get-swath-width collection) 
     :period (:OrbitPeriod orbit-params)
     :inclination-angle (:InclinationAngle orbit-params)
     :number-of-orbits (:NumberOfOrbits orbit-params)
     :start-circular-latitude (:StartCircularLatitude orbit-params)}))

(defn collection-spatial->elastic-docs
  "Converts the spatial area of the given collection to the elastic documents"
  [coordinate-system collection]
  (when-let [shapes (seq (get-collection-geometry-shapes collection))]
    (shapes->elastic-doc shapes coordinate-system)))

(defn granule-spatial->elastic-docs
  "Converts the spatial area of the given granule to the elastic documents"
  [coordinate-system granule]
  (when-let [geometries (get-in granule [:spatial-coverage :geometries])]
    (shapes->elastic-doc geometries coordinate-system)))
