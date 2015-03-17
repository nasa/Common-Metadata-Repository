(ns cmr.umm.iso-mends.spatial
  "Functions for extracting spatial extent information from an
  ISO-MENDS format XML document."
  (:require [clojure.data.xml :as x]
            [clojure.string :as string]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.spatial.derived :as d]
            [cmr.spatial.line-string :as ls]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.umm.collection :as c]
            [cmr.umm.spatial :as umm-s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing XML

(defmulti ^:private parse-geo-element :tag)

(defmulti ^:private parse-gml :tag)

;; EX_BoundingPolygon always contains a single gmd:polygon, which in
;; turn contains a single gml:Point, gml:LineString, or gml:Polygon

(defmethod parse-geo-element :EX_BoundingPolygon
  [element]
  (let [gmd-polygon (-> element :content first)
        gml-element (-> gmd-polygon :content first)]
    (parse-gml gml-element)))

(defn- parse-points
  "Returns a sequence of Points from an element with a gml:pos or gml:posList."
  [element]
  (when-let [pos-str (or (cx/string-at-path element [:pos])
                         (cx/string-at-path element [:posList]))]
    (->> (re-seq #"[\-\w\.]+" pos-str)
         (map #(Double. %))
         (partition 2)
         (map #(apply p/point %)))))

(defmethod parse-gml :Point
  [element]
  (first (parse-points element)))

(defmethod parse-gml :LineString
  [element]
  (ls/line-string :geodetic (parse-points element)))

(defmethod parse-gml :Polygon
  [element]
  (println "parsing polgyon element" element)
  (let [exterior  (cx/element-at-path element [:exterior :LinearRing])
        interiors (cx/elements-at-path element [:interior :LinearRing])
        rings     (cons (parse-points exterior) (map parse-points interiors))]
    (poly/polygon :geodetic rings)))

(defmethod parse-geo-element :default
  [_]
  nil)

(defmethod parse-geo-element :EX_GeographicBoundingBox
  [element]
  (let [west (cx/double-at-path element [:westBoundLongitude :Decimal])
        east (cx/double-at-path element [:eastBoundLongitude :Decimal])
        north (cx/double-at-path element [:northBoundLatitude :Decimal])
        south (cx/double-at-path element [:southBoundLatitude :Decimal])]
    (mbr/mbr west north east south)))

;;; ISO MENDS represents all points, lines, and closed shapes as
;;; gmd:EX_BoundingPolygon elements containing a sequence of gml
;;; shapes.

(defn- parse-geometry
  [xml]
  (let [geo-elems (cx/elements-at-path xml [:extent :EX_Extent :geographicElement])
        ;; ISO MENDS includes bounding boxes for each element (point,
        ;; polygon, etc.) in the spatial extent metadata. We can
        ;; discard the redundant bounding boxes.
        shape-elems (map second (partition 2 geo-elems))]
    (remove nil? (map (comp parse-geo-element first :content) shape-elems))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Generating XML

;;; Helpers for building various elements

(defn pos-str
  [& points]
  (string/join " " (map util/double->string (mapcat (juxt :lat :lon) points))))

(def pos-attrs
  {:srsName "http://www.opengis.net/def/crs/EPSG/4326"
   :srsDimension "2"})

(defn gml-pos
  [point]
  (x/element :gml:pos pos-attrs (pos-str point)))

(defn gml-poslist
  [points]
  (x/element :gml:posList pos-attrs (apply pos-str points)))

(defn gmd-poly
  [content]
  (x/element :gmd:EX_BoundingPolygon {}
             (x/element :gmd:polygon {}
                        content)))

(defn gml-linear-ring
  [points]
  (x/element :gml:LinearRing {}
             (gml-poslist points)))

(defn gco-decimal
  "Returns n as a gco:Decimal element"
  [n]
  (x/element :gco:Decimal {} (util/double->string n)))

(def gml-id-state (atom 0))

(defn gen-id
  []
  (str "geo-" (swap! gml-id-state inc)))

;;; Rendering the ISO MENDS MBR element for each geometry type

(defmulti geometry->iso-mbr type)

(defmulti geometry->iso-geom
  "Returns content of a ISO MENDS extent element for an individual UMM
  spatial coverage geometry record. Dispatches on type."
  type)

(defmethod geometry->iso-mbr cmr.spatial.point.Point
  [point]
  (mbr/point->mbr point))

(defmethod geometry->iso-mbr cmr.spatial.line_string.LineString
  [line]
  (ls/line-string->mbr line))

(defmethod geometry->iso-mbr cmr.spatial.polygon.Polygon
  [polygon]
  (:mbr polygon))

(defmethod geometry->iso-mbr cmr.spatial.mbr.Mbr
  [mbr]
  mbr)

;;; Rendering gmd:geographicElement content

(defmethod geometry->iso-geom cmr.spatial.point.Point
  [point]
  (gmd-poly (x/element :gml:Point {:gml:id (gen-id)} (gml-pos point))))

(defmethod geometry->iso-geom cmr.spatial.line_string.LineString
  [{:keys [points]}]
  (gmd-poly (x/element :gml:LineString {:gml:id (gen-id)}
                       (gml-poslist points))))

(defmethod geometry->iso-geom cmr.spatial.polygon.Polygon
  [polygon]
  (let [exterior (poly/boundary polygon)
        interior (poly/holes polygon)]
    (gmd-poly
     (x/element :gml:Polygon {:gml:id (gen-id)}
                (x/element :gml:exterior {} (gml-linear-ring (:points exterior)))
                (when-not (empty? interior)
                  (map #(x/element :gml/interior {} (gml-linear-ring (:points %))) interior))))))

(defmethod geometry->iso-geom cmr.spatial.mbr.Mbr
  [mbr]
  (x/element :gmd:EX_GeographicBoundingBox {}
             (x/element :gmd:eastBoundingLongitude {} (gco-decimal (:east mbr)))
             (x/element :gmd:westBoundingLongitude {} (gco-decimal (:west mbr)))
             (x/element :gmd:northBoundingLatitude {} (gco-decimal (:north mbr)))
             (x/element :gmd:southBoundingLatitude {} (gco-decimal (:south mbr)))))

(defn geometry->iso-xml
  "Returns an individual ISO MENDS geographic extent element for a UMM
  spatial coverage geometry record."
  [geom]
  (let [geom (d/calculate-derived (umm-s/set-coordinate-system :geodetic geom))]
    (list
     (x/element :gmd:geographicElement {} (geometry->iso-geom (geometry->iso-mbr geom)))
     (x/element :gmd:geographicElement {} (geometry->iso-geom geom)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions

(defn iso-data-id->SpatialCoverage
  "Returns a UMM spatial coverage record from an ISO MENDS MD_DataIdentification element."
  [data-id]
  (c/map->SpatialCoverage
   {:geometries (parse-geometry data-id)}))

(defn SpatialCoverage->xml
  "Returns a sequence of ISO MENDS elements for the given UMM spatial
  coverage record."
  [spatial]
  (mapcat geometry->iso-xml (:geometries spatial)))
