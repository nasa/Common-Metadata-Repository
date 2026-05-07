(ns cmr.elasticsearch.plugins.spatial.script.core
  (:require
   [clojure.string :as string]
   [cmr.common.util :as u]
   [cmr.spatial.s2geometry.cells :as s2-cells]
   [cmr.spatial.serialize :as srl])
  (:import
   (cmr.spatial.mbr Mbr)
   (cmr.spatial.polygon Polygon)
   (com.google.common.geometry S2LatLngRect S2Polygon S2Point)
   (java.util Map)
   (org.apache.logging.log4j LogManager)
   (org.elasticsearch.script DocReader
                             LeafReaderContextSupplier)
   (org.elasticsearch.search.lookup FieldLookup
                                    LeafDocLookup
                                    LeafStoredFieldsLookup
                                    LeafSearchLookup
                                    SearchLookup))
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScript
   :extends org.elasticsearch.script.FilterScript
   :constructors {[java.lang.Object
                   java.util.Map
                   org.elasticsearch.search.lookup.SearchLookup
                   org.elasticsearch.script.DocReader
                   java.lang.Object
                   String]
                  [java.util.Map
                   org.elasticsearch.search.lookup.SearchLookup
                   org.elasticsearch.script.DocReader]}
   :methods [[getFields [] org.elasticsearch.search.lookup.LeafStoredFieldsLookup]
             [getDoc [] org.elasticsearch.search.lookup.LeafDocLookup]]
   :init init
   :state data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script helper functions                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-from-fields
  [^LeafStoredFieldsLookup lookup key]
  (when (and lookup key)
    (when-let [^FieldLookup field-lookup (.get lookup key)]
      (seq (.getValues field-lookup)))))

;; Original function
(defn doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [^LeafStoredFieldsLookup lookup intersects-fn]

  (if-let [ords-info (get-from-fields lookup "ords-info")]
    (let [ords (get-from-fields lookup "ords")
          shapes (srl/ords-info->shapes ords-info ords)]
      (try
        ;; Must explicitly return true or false or elastic search will complain
        (if (u/any-true? intersects-fn shapes)
          true
          false)
        (catch Throwable t
          (.error (LogManager/getLogger "cmr_spatial_script") t)
          (throw (ex-info "An exception occurred checking for intersections" {:shapes shapes} t)))))
    false))

;; TODO These intersects are totally correct, they assume everything is geodetic and don't handle cartesian shapes correctly. But they are good enough to get a base performance comparison
(defmulti s2-polygon-intersects-shape?
  (fn [_polygon shape]
    (class shape)))

(defmethod s2-polygon-intersects-shape? :default
  [_polygon shape]
  (throw (ex-info (format "s2-polygon-intersects-shape? Unsupported shape type [%s]" (class shape)) {:query-shape shape})))

(defmethod s2-polygon-intersects-shape? Polygon
  [s2-polygon shape]
  (let [shape-s2-polygon (s2-cells/shape->s2polygon shape)]
    (.intersects s2-polygon shape-s2-polygon)))

(defmethod s2-polygon-intersects-shape? Mbr
  [s2-polygon shape]
  (let [;;polygon-bounding-rect (.getRectBound s2-polygon)
        shape-s2-rectangle (s2-cells/shape->s2latlngrect shape)
        shape-s2-polygon (s2-cells/s2-rect->s2-polygon shape-s2-rectangle)]
    (.intersects s2-polygon shape-s2-polygon)))

(defmulti s2-rect-contains-shape?
  (fn [_rect shape]
    (class shape)))

(defmethod s2-rect-contains-shape? :default
  [_rect shape]
  (throw (ex-info (format "s2-rect-contains-shape? Unsupported shape type [%s]" (class shape)) {:query-shape shape})))

(defmethod s2-rect-contains-shape? Mbr
  [s2-rect shape]
  (let [shape-s2-rectangle (s2-cells/shape->s2latlngrect shape)
        shape-s2-polygon (s2-cells/s2-rect->s2-polygon shape-s2-rectangle)
        query-s2-polygon (s2-cells/s2-rect->s2-polygon s2-rect)]
    (.intersects query-s2-polygon shape-s2-polygon)))

(defmethod s2-rect-contains-shape? Polygon
  [s2-rect shape]
  (let [query-s2-polygon (s2-cells/s2-rect->s2-polygon s2-rect)
        shape-s2-polygon (s2-cells/shape->s2polygon shape)]
    (.intersects query-s2-polygon shape-s2-polygon)))

(defmulti shapes-intersect?
  "Returns true if any of the shapes in the doc intersect with the query polygon. This is an expensive check that should only be done if there is a potential match based on the cell tokens."
  (fn [query-shape _lookup _ords-info]
    (class query-shape)))

(defmethod shapes-intersect? :default
  [query-shape _lookup _ords-info]
  (throw (ex-info (format "shapes-intersect? Unsupported shape type [%s]" (class query-shape)) {:query-shape query-shape})))

(defmethod shapes-intersect? S2Polygon
  [s2-polygon lookup ords-info]
  (let [;; s2-polygon (s2-cells/shape->s2polygon query-shape)
        ords (get-from-fields lookup "ords")
        shapes (srl/ords-info->shapes ords-info ords)
        ;; If the shape is a polygon, or circle we can directly check for intersection with the query polygon.
        ;; If the shape is an MBR, we need to get the bound rect and then run contains
        ;; if the shape is a point we can directly call contains-shape? with the query polygon
        ;; shapes-s2-polygon (try
        ;;                     (s2-cells/shape->s2polygon (first shapes))
        ;;                     (catch Throwable t
        ;;                       (.error (LogManager/getLogger "cmr_spatial_script") (format "Unable to convert shapes [%s] to S2Polygon" shapes) t)
        ;;                       (throw (ex-info "An exception occurred converting shapes to S2Polygon" {:shapes shapes} t))))
        ]
    (boolean (s2-polygon-intersects-shape? s2-polygon (first shapes)))))

(defmethod shapes-intersect? S2LatLngRect
  [s2-rectangle lookup ords-info]
  (let [;; s2-rectangle (s2-cells/shape->s2latlngrect query-shape)
        ords (get-from-fields lookup "ords")
        shapes (srl/ords-info->shapes ords-info ords)
        ;; if the shape the shape is an MBR we run a contains check
        ;; if the shape is a polygon we call getBoundRect and then run a contains check
        ]
    (boolean (s2-rect-contains-shape? s2-rectangle (first shapes)))))

(defn s2-doc-intersects?
  "Returns true if the doc contains a ring that intersects the ring passed in."
  [lookup query-shape]
  (if-let [ords-info (get-from-fields lookup "ords-info")]
    (try
      (if (shapes-intersect? query-shape lookup ords-info)
        true
        false)
      (catch Throwable t
        (.error (LogManager/getLogger "cmr_spatial_script") t)
        (throw (ex-info "An exception occurred checking for intersections" {:ords-info ords-info} t))))
    false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script helper functions                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         Begin script functions                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'cmr.elasticsearch.plugins.SpatialScript)

(defn ^LeafStoredFieldsLookup -getFields
  [^SpatialScript this]
  (-> this .data :search-lookup (.fields)))

(defn ^LeafDocLookup -getDoc
  [^SpatialScript this]
  (-> this .data :search-lookup (.doc)))

;; Need to override setDocument for more control over lookup
(defn -setDocument
  [^SpatialScript this doc-id]
  (-> this .data :search-lookup (.setDocument doc-id)))

(defn- -init [^Object intersects-fn ^Map params ^SearchLookup lookup ^DocReader doc-reader ^Object query-shape ^String use-s2]
  (let [context (when (instance? LeafReaderContextSupplier doc-reader)
                  (.getLeafReaderContext ^LeafReaderContextSupplier doc-reader))]
    [[params lookup doc-reader] {:intersects-fn intersects-fn
                                 :search-lookup (.getLeafSearchLookup lookup context)
                                 :query-shape query-shape
                                 :use-s2 use-s2}]))


(defn -execute [^SpatialScript this]
  (if (= "true" (-> this .data :use-s2))
    (s2-doc-intersects? (.getFields this)
                        (-> this .data :query-shape))
    (doc-intersects? (.getFields this)
                    (-> this .data :intersects-fn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                         End script functions                              ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
