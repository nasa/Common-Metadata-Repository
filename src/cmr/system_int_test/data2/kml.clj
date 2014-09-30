(ns cmr.system-int-test.data2.kml
  "Contains functions for parsing kml results into spatial shapes."
  (:require [clojure.data.xml :as x]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.line-string :as l]
            [cmr.common.xml :as cx]
            [clojure.string :as str])
  (:import cmr.umm.collection.UmmCollection
           cmr.umm.granule.UmmGranule
           cmr.spatial.mbr.Mbr))

(defn coordinates-container-elem->points
  "Takes an XML element that contains a child called 'coordinates' and returns a list of the points
  represented by the coordinates."
  [shape-elem]
  (->> (str/split (cx/string-at-path shape-elem [:coordinates]) #" ")
       (map #(str/split % #","))
       (map (fn [[lon-s lat-s]]
              (p/point (Double/parseDouble lon-s) (Double/parseDouble lat-s))))))

(defn ring-elem->ring
  "Converts an XML ring element into a real ring."
  [coordinate-system ring-elem]
  (rr/ring coordinate-system (coordinates-container-elem->points ring-elem)))

(defmulti shape-element->shape
  "Converts an XML shape element into a cmr spatial shape record."
  (fn [coordinate-system shape-elem]
    (:tag shape-elem)))

(defmethod shape-element->shape :Polygon
  [coordinate-system shape-elem]
  (let [boundary (ring-elem->ring
                   coordinate-system
                   (cx/element-at-path shape-elem [:outerBoundaryIs :LinearRing]))
        holes (map (partial ring-elem->ring coordinate-system)
                   (cx/elements-at-path shape-elem [:innerBoundaryIs :LinearRing]))
        boundary-points (:points boundary)]

    (if (and (= coordinate-system :cartesian)
             (empty? holes)
             (not= (first boundary-points) (last boundary-points)))
      ;; A non closed cartesian ring is actually a line string
      (l/line-string coordinate-system boundary-points)
      (poly/polygon coordinate-system (cons boundary holes)))))

(defmethod shape-element->shape :LineString
  [coordinate-system shape-elem]
  (l/line-string coordinate-system (coordinates-container-elem->points shape-elem)))

(defmethod shape-element->shape :Point
  [coordinate-system shape-elem]
  (first (coordinates-container-elem->points shape-elem)))

(defn placemark-elem->item
  "Converts a KML placemark element into the spatial item representation."
  [placemark-elem]
  (let [item-name (cx/string-at-path placemark-elem [:name])
        coordinate-system (->> (cx/string-at-path placemark-elem [:styleUrl])
                               (re-matches #".*((?:cartesian)|(?:geodetic)).*")
                               last
                               keyword)
        shape-elements (or (:content (cx/element-at-path placemark-elem [:MultiGeometry]))
                           [(-> placemark-elem :content last)])]
    {:name item-name
     :shapes (map (partial shape-element->shape coordinate-system) shape-elements)}))

(defn parse-kml-results
  "Takes kml as a string and returns expected items which will contain a name and a list of shapes"
  [kml-string]
  (map placemark-elem->item (cx/elements-at-path (x/parse-str kml-string) [:Document :Placemark])))

(defmulti shape->kml-representation
  "Converts a CMR spatial shape into the KML representation of that shape"
  (fn [shape]
    (type shape)))

(defmethod shape->kml-representation :default
  [shape]
  shape)

(defmethod shape->kml-representation Mbr
  [br]
  (let [points (reverse (m/corner-points br))]
    (poly/polygon
      :cartesian
      [(rr/ring :cartesian (concat points [(first points)]))])))

(defmulti umm-item->expected-kml-result
  (fn [umm-item]
    (type umm-item)))

(defmethod umm-item->expected-kml-result UmmCollection
  [umm-item]
  {:name (:entry-title umm-item)
   :shapes (map shape->kml-representation (get-in umm-item [:spatial-coverage :geometries]))})

(defmethod umm-item->expected-kml-result UmmGranule
  [umm-item]
  {:name (:granule-ur umm-item)
   :shapes (map shape->kml-representation (get-in umm-item [:spatial-coverage :geometries]))})

(defn umm-items->expected-kml-results
  [expected-items]
  {:status 200
   :results (set (map umm-item->expected-kml-result expected-items))})

(defn kml-results-match?
  "Returns true if the kml results are for the expected items"
  [expected-items actual-result]
  (= (umm-items->expected-kml-results expected-items)
     (update-in actual-result [:results] set)))

(comment

  (def sample-kml (slurp "/Users/jgilman/Desktop/sample_cmr.kml"))

  (parse-kml-results sample-kml)

  (def placemarks
    (cx/elements-at-path (x/parse-str sample-kml) [:Document :Placemark]))

(first placemarks)

  (some-> (cx/element-at-path p [:MultiGeometry]))

  (-> p :content )


  )