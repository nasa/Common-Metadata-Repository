(ns cmr.spatial.kml
  "Contains functions for returning the spatial areas as KML"
  (:require [clojure.string :as str]
            [clojure.data.xml :as x]
            [clojure.java.shell :as shell]
            [cmr.spatial.relations :as relations]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.geodetic-ring :as gr]
            [cmr.spatial.cartesian-ring :as cr]
            [cmr.spatial.line-string :as l]))


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

(defn- shape->kml-placemark
  [shape]
  (let [coordinate-system (relations/coordinate-system shape)]
    (x/element
     :Placemark {}
     (x/element :name {} "none")
     (x/element :styleUrl {} (coordinate-system->style-url coordinate-system))
     (shape->xml-element shape))))

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

(defn shapes->kml
  "Returns the shapes in a KML document."
  [shapes]
  (x/emit-str
   (x/element :kml KML_XML_NAMESPACE_ATTRIBUTES
              (x/element :Document {}
                         kml-geodetic-style-xml-elem
                         kml-cartesian-style-xml-elem
                         (map shape->kml-placemark shapes)))))


(defn display-shapes
  "Development time helper that saves the shapes as KML and opens the file in Google Earth."
  ([shapes]
   (display-shapes shapes "ge_scratch.kml"))
  ([shapes filename]
   (spit filename (shapes->kml shapes))
   (shell/sh "open" filename)))
