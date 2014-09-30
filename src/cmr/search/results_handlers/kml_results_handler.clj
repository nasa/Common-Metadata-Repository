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
            [cmr.common.util :as util]))


(defmethod elastic-search-index/concept-type+result-format->fields [:collection :kml]
  [concept-type query]
  ["entry-title"
   "ords-info"
   "ords"
   ])

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :kml]
  [concept-type query]
  ["granule-ur"
   ;; TODO we need to support returning the orbit polygons as kml as well.
   "ords-info"
   "ords"
   ])

(defmethod elastic-results/elastic-result->query-result-item :kml
  [context query elastic-result]
  (let [{[granule-ur] :granule-ur
         [entry-title] :entry-title
         ords-info :ords-info
         ords :ords} (:fields elastic-result)]
    (util/remove-nil-keys
      {:name (or granule-ur entry-title)
       :shapes (srl/ords-info->shapes ords-info ords)})))

(defn- item->coordinate-system
  "Returns the coordinate system to use for the item. Also Verifies that an item (granule or
  collection) only has shapes in a single coordinate system sincea single KML placemark can only
  have a single style and the styles are used to correctly represent areas in cartesian or geodetic."
  [item]
  (let [coordinate-systems (->> (:shapes item)
                               (map relations/coordinate-system)
                               distinct
                               ;; point coordinate system will be nil
                               (remove nil?))]
    (when (> (count coordinate-systems) 1)
      (errors/internal-error!
        (format "Found granule [%s] with more than one coordinate system for it's shapes: %s"
                (:name item) (pr-str (:shapes item)))))
    (or (first coordinate-systems)
        ;; If there were only points then there won't be a specific coordinate system. Geodetic
        ;; will work for points in KML
        :geodetic)))

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

(defn- item->kml-placemark
  "Converts a single item into a KML placemark xml element"
  [item]
  (let [coordinate-system (item->coordinate-system item)
        style-url (str "#" (name coordinate-system) "_style")
        shapes (:shapes item)]
    (x/element :Placemark {}
               (x/element :name {} (:name item))
               (x/element :styleUrl {} style-url)
               (cond
                 (> (count shapes) 1)
                 (x/element :MultiGeometry {}
                            (map shape->xml-element shapes))

                 (= (count shapes) 1)
                 (shape->xml-element (first shapes))

                 :else
                 (x/xml-comment "No spatial area")))))

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
                            (map item->kml-placemark items))))))






