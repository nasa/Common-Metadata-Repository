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
            [cmr.spatial.relations :as r]
            [cmr.umm.collection :as c]
            [cmr.umm.iso-mends.core :as core]
            [cmr.umm.spatial :as umm-s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing XML

(defmulti ^:private parse-geo-element
  "Returns a UMM geometry values from the child element of ISO extent
  geographicElement elements. Dispatches on tag of element."
  :tag)

(defmulti ^:private parse-gml
  "Returns a UMM geometry value for a gml:* child of a
  EX_BoundingPolygon element. Dispatches on tag."
  :tag)

;; EX_BoundingPolygon always contains a single gmd:polygon, which in
;; turn contains a single gml:Point, gml:LineString, or gml:Polygon

(defmethod parse-geo-element :EX_BoundingPolygon
  [element]
  (let [gmd-polygon (-> element :content first)
        gml-element (-> gmd-polygon :content first)]
    (parse-gml gml-element)))

(defmethod parse-gml :Point
  [element]
  (-> (cx/string-at-path element [:pos])
      umm-s/lat-lon-point-str->points
      first))

(defmethod parse-gml :LineString
  [element]
  (-> (cx/string-at-path element [:posList])
      umm-s/lat-lon-point-str->points
      ls/line-string))

(defmethod parse-gml :Polygon
  [element]
  (let [close-ring (fn [points]
                     (let [v (vec points)]
                       (if (= (first v) (last v))
                         v
                         (conj v (first v)))))
        exterior  (-> (cx/string-at-path element [:exterior :LinearRing :posList])
                      umm-s/lat-lon-point-str->points
                      close-ring)
        interiors (->> (cx/strings-at-path element [:interior :LinearRing :posList])
                       (map umm-s/lat-lon-point-str->points)
                       (map close-ring))
        rings     (cons (umm-s/ring exterior)
                        (map umm-s/ring interiors))]
    (poly/polygon rings)))

(defmethod parse-geo-element :default
  [_]
  nil)

(defmethod parse-geo-element :EX_GeographicBoundingBox
  [element]
  (let [west  (cx/double-at-path element [:westBoundLongitude :Decimal])
        east  (cx/double-at-path element [:eastBoundLongitude :Decimal])
        north (cx/double-at-path element [:northBoundLatitude :Decimal])
        south (cx/double-at-path element [:southBoundLatitude :Decimal])]
    (mbr/mbr west north east south)))

(defn- parse-geometries
  "Returns a seq of UMM geometry records from an ISO XML document."
  [xml]
  (let [id-elem   (core/id-elem xml)
        geo-elems (cx/elements-at-path id-elem [:extent :EX_Extent :geographicElement])
        ;; ISO MENDS includes bounding boxes for each element (point,
        ;; polygon, etc.) in the spatial extent metadata. We can
        ;; discard the redundant bounding boxes.
        shape-elems (map second (partition 2 geo-elems))]
    (seq (remove nil? (map #(parse-geo-element (first (:content %))) shape-elems)))))

(def ref-sys-path-with-ns
  "A namespaced element path sequence for the ISO MENDS coordinate system element."
  [:gmd:referenceSystemInfo :gmd:MD_ReferenceSystem :gmd:referenceSystemIdentifier :gmd:RS_Identifier :gmd:code :gco:CharacterString])

(def ref-sys-path
  "The (non-namespaced) path to access the ISO MENDS coordinate system element."
  (map #(keyword (second (.split (name %) ":"))) ref-sys-path-with-ns))

(defn- parse-coordinate-system
  [xml]
  (umm-s/->coordinate-system (cx/string-at-path xml ref-sys-path)))

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

(defmulti geometry->iso-geom
  "Returns content of a ISO MENDS extent element for an individual UMM
  spatial coverage geometry record obj. Dispatches on the geometry
  record type. gen-id must be a function which returns a
  document-level unique ID for a geographicElement."
  (fn [obj gen-id] (type obj)))

;;; Rendering gmd:geographicElement content

(defmethod geometry->iso-geom cmr.spatial.point.Point
  [point gen-id]
  (gmd-poly (x/element :gml:Point {:gml:id (gen-id)} (gml-pos point))))

(defmethod geometry->iso-geom cmr.spatial.line_string.LineString
  [{:keys [points]} gen-id]
  (gmd-poly (x/element :gml:LineString {:gml:id (gen-id)}
                       (gml-poslist points))))

(defmethod geometry->iso-geom cmr.spatial.polygon.Polygon
  [polygon gen-id]
  (let [exterior (:points (poly/boundary polygon))
        interior (map :points (poly/holes polygon))]
    (gmd-poly
     (x/element :gml:Polygon {:gml:id (gen-id)}
                (x/element :gml:exterior {} (gml-linear-ring exterior))
                (when (seq interior)
                  (map #(x/element :gml:interior {} (gml-linear-ring %)) interior))))))

(defmethod geometry->iso-geom cmr.spatial.mbr.Mbr
  [mbr gen-id]
  (x/element :gmd:EX_GeographicBoundingBox {}
             (x/element :gmd:westBoundLongitude {} (gco-decimal (:west mbr)))
             (x/element :gmd:eastBoundLongitude {} (gco-decimal (:east mbr)))
             (x/element :gmd:southBoundLatitude {} (gco-decimal (:south mbr)))
             (x/element :gmd:northBoundLatitude {} (gco-decimal (:north mbr)))))

(defn geometry->iso-xml
  "Returns an individual ISO MENDS geographic extent element for a UMM
  spatial coverage geometry record."
  [coordinate-system geom gen-id]
  ;; set the coordinate system based on the spatial coverage for output
  (let [geom     (d/calculate-derived (umm-s/set-coordinate-system coordinate-system geom))]
    (list
     (x/element :gmd:geographicElement {} (geometry->iso-geom (r/mbr geom) gen-id))
     (x/element :gmd:geographicElement {} (geometry->iso-geom geom gen-id)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions

(defn xml-elem->SpatialCoverage
  "Returns a UMM SpatialCoverage record from the given ISO MENDS XML
  document root element."
  [xml]
  (let [geometries (parse-geometries xml)
        coord-sys  (parse-coordinate-system xml)]
    (when geometries
      (c/map->SpatialCoverage
       {:spatial-representation coord-sys
        :granule-spatial-representation coord-sys
        :geometries geometries}))))

(defn spatial-coverage->coordinate-system-xml
  "Returns ISO MENDS coordinate system XML element from the given SpatialCoverage."
  [{:keys [spatial-representation]}]
  (when spatial-representation
    (reduce (fn [content tag] (x/element tag {} content))
            (.toUpperCase (name spatial-representation))
            (reverse ref-sys-path-with-ns))))

(defn spatial-coverage->extent-xml
  "Returns a sequence of ISO MENDS elements from the given SpatialCoverage."
  [{:keys [spatial-representation geometries]}]
  (let [id-state (atom 0)
        ;; a function to generate a unique (per-document) id for
        ;; geographic elements
        gen-id   (fn [] (str "geo-" (swap! id-state inc)))]
    (mapcat #(geometry->iso-xml spatial-representation % gen-id) geometries)))
