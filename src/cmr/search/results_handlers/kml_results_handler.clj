(ns cmr.search.results-handlers.kml-results-handler
  "Handles the returning search results in KML format (keyhole markup language for Google Earth etc)"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.common.services.errors :as errors]
            [clojure.data.xml :as x]
            [clojure.string :as str]
            [cmr.search.models.results :as r]
            [cmr.spatial.serialize :as srl]
            [cmr.spatial.relations :as relations]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.line-string :as l]
            [cmr.common.util :as util]
            [cmr.search.results-handlers.orbit-swath-results-helper :as orbit-swath-helper]))


(defmethod elastic-search-index/concept-type+result-format->fields [:collection :kml]
  [concept-type query]
  ["entry-title"
   "ords-info"
   "ords"])

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :kml]
  [concept-type query]
  (vec (into #{"granule-ur" "ords-info" "ords"}
             orbit-swath-helper/orbit-elastic-fields)))

(defn collection-elastic-result->query-result-item
  [elastic-result]
  (let [{[granule-ur] :granule-ur
         [entry-title] :entry-title
         ords-info :ords-info
         ords :ords} (:fields elastic-result)]
    {:name (or granule-ur entry-title)
     :shapes (srl/ords-info->shapes ords-info ords)}))

(defn granule-elastic-result->query-result-item
  [orbits-by-collection elastic-result]
  (let [{[granule-ur] :granule-ur
         [entry-title] :entry-title
         ords-info :ords-info
         ords :ords} (:fields elastic-result)
        shapes (concat (srl/ords-info->shapes ords-info ords)
                       (orbit-swath-helper/elastic-result->swath-shapes
                         orbits-by-collection elastic-result))]
    {:name (or granule-ur entry-title)
     :shapes shapes}))

(defn- granule-elastic-results->query-result-items
  [context query elastic-matches]
  (let [orbits-by-collection (orbit-swath-helper/get-orbits-by-collection context elastic-matches)]
    (pmap (partial granule-elastic-result->query-result-item orbits-by-collection) elastic-matches)))

(defmethod elastic-results/elastic-results->query-results :kml
  [context query elastic-results]
  (let [hits (get-in elastic-results [:hits :total])
        elastic-matches (get-in elastic-results [:hits :hits])
        items (if (= :granule (:concept-type query))
                (granule-elastic-results->query-result-items context query elastic-matches)
                (map collection-elastic-result->query-result-item elastic-matches))]
    (r/map->Results {:hits hits :items items :result-format (:result-format query)})))


(defprotocol KmlSpatialShapeHandler
  (shape->xml-element
    [shape]
    "Converts a spatial shape into the KML XML element"))

(defn- points-map->coordinates-element
  "Converts a map containing :points into the coordinates representation in KML"
  [{:keys [points]}]
  (x/element :coordinates {}
             (str/join " " (map #(str (:lon %) "," (:lat %)) points))))

(extend-protocol KmlSpatialShapeHandler

  cmr.spatial.point.Point
  (shape->xml-element
    [point]
    (x/element :Point {}
               (x/element :coordinates {}
                          (str (:lon point) "," (:lat point)))))

  cmr.spatial.line_string.LineString
  (shape->xml-element
    [line]
    (if (= :geodetic (:coordinate-system line))
      (x/element :LineString {}
                 (points-map->coordinates-element line))
      ;; A cartesian line string is drawn appropriately in KML if it is represented as a non-closed
      ;; polygon using the cartesian style.
      [(x/xml-comment (str "CMR representation is a line. Cartesian Lines are represented as non-closed,"
                           " filled polygons with 0 opacity to be correctly rendered in Google Earth."))
       (shape->xml-element (poly/polygon :cartesian [(cr/ring (:points line))]))]))

  cmr.spatial.mbr.Mbr
  (shape->xml-element
    [mbr]
    (let [points (reverse (m/corner-points mbr))]
      [(x/xml-comment "CMR representation is a bounding box.")
       (shape->xml-element
         ;; An mbr is represented as a cartesian polygon.
         (poly/polygon
           :cartesian
           [(cr/ring (concat points [(first points)]))]))]))

  cmr.spatial.geodetic_ring.GeodeticRing
  (shape->xml-element
    [ring]
    (x/element :LinearRing {}
               (points-map->coordinates-element ring)))

  cmr.spatial.cartesian_ring.CartesianRing
  (shape->xml-element
    [ring]
    (x/element :LinearRing {}
               (points-map->coordinates-element ring)))

  cmr.spatial.polygon.Polygon
  (shape->xml-element
    [polygon]
    (let [boundary (poly/boundary polygon)
          holes (poly/holes polygon)]
      (x/element :Polygon {}
                 (x/element :tessellate {} "1")
                 (x/element :outerBoundaryIs {} (shape->xml-element boundary))
                 (for [hole holes]
                   (x/element :innerBoundaryIs {} (shape->xml-element hole)))))))

(def coordinate-system->style-url
  "A map of coordinate system to the style url to use in a placemark."
  {:geodetic "#geodetic_style"
   :cartesian "#cartesian_style"})

(defn- item->kml-placemarks
  "Converts a single item into KML placemarks xml element. Most of the time an item will become a single
  placemark. In the event an item has both geodetic areas and cartesian as with geodetic polygons and
  bounding boxes it will be written as two placemarks"
  [item]
  (if-let [shapes (seq (:shapes item))]
    (let [shapes-by-coord-sys (group-by #(or (relations/coordinate-system %) :geodetic) (:shapes item))
          multiple-placemarks? (> (count shapes-by-coord-sys) 1)]
      (for [[coordinate-system shapes] shapes-by-coord-sys]
        (x/element :Placemark {}
                   (x/element :name {} (if multiple-placemarks?
                                         (str (:name item) "_" (name coordinate-system))
                                         (:name item)))
                   (x/element :styleUrl {} (coordinate-system->style-url coordinate-system))
                   (if (> (count shapes) 1)
                     (x/element :MultiGeometry {}
                                (map shape->xml-element shapes))
                     (shape->xml-element (first shapes))))))
    [(x/element :Placemark {}
                (x/element :name {} (:name item))
                (x/xml-comment "No spatial area"))]))

(def KML_XML_NAMESPACE_ATTRIBUTES
  "The set of attributes that go on the KML root element"
  {:xmlns "http://www.opengis.net/kml/2.2"})

(def kml-geodetic-style-xml-elem
  "A clojure data.xml element representing the XML element containing the geodetic style"
  (x/element :Style {:id "geodetic_style"}
             (x/element :LineStyle {}
                        (x/element :color {} "ffffffff")
                        (x/element :colorMode {} "random")
                        (x/element :width {} "2"))
             (x/element :IconStyle {}
                        (x/element :color {} "ffffffff")
                        (x/element :colorMode {} "random")
                        (x/element :scale {} "3.0"))
             (x/element :PolyStyle {}
                        ;; Fill of 0 makes it not filled and polygon lines are drawn following
                        ;; shortest path arcs.
                        (x/element :fill {} "0"))))

(def kml-cartesian-style-xml-elem
  "A clojure data.xml element representing the XML element containing the cartesian style"
  (x/element :Style {:id "cartesian_style"}
             (x/element :LineStyle {}
                        (x/element :color {} "ffffffff")
                        (x/element :colorMode {} "random")
                        (x/element :width {} "2"))
             (x/element :IconStyle {}
                        (x/element :color {} "ffffffff")
                        (x/element :colorMode {} "random")
                        (x/element :scale {} "3.0"))
             (x/element :PolyStyle {}
                        ;;Fill of 0 opacity. Filled polygons are drawn in cartesian style. The
                        ;; opacity of 0 makes them see through so they look like geodetic polygons
                        ;; which have no fill.
                        (x/element :color {} "00ffffff"))))

(defmethod qs/search-results->response :kml
  [context query results]
  (let [items (:items results)
        xml-fn (if (:pretty? query) x/indent-str x/emit-str)]
    (xml-fn
      (x/element :kml KML_XML_NAMESPACE_ATTRIBUTES
                 (x/element :Document {}
                            kml-geodetic-style-xml-elem
                            kml-cartesian-style-xml-elem
                            (mapcat item->kml-placemarks items))))))






