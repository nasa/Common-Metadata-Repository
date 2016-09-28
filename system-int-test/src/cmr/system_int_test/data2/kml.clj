(ns cmr.system-int-test.data2.kml
  "Contains functions for parsing kml results into spatial shapes."
  (:require
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [clojure.test]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.relations :as r]
   [cmr.spatial.ring-relations :as rr]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.fast-xml :as fx])
  (:import
   (cmr.spatial.mbr Mbr)
   (cmr.umm.umm_granule UmmGranule)
   (cmr.umm.umm_collection UmmCollection)))

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
        coordinate-system (some->> (cx/string-at-path placemark-elem [:styleUrl])
                                   (re-matches #".*((?:cartesian)|(?:geodetic)).*")
                                   last
                                   keyword)
        shape-elements (when coordinate-system
                         (or (:content (cx/element-at-path placemark-elem [:MultiGeometry]))
                             [(-> placemark-elem :content last)]))]
    (util/remove-nil-keys
      {:name item-name
       :shapes (seq (map (partial shape-element->shape coordinate-system) shape-elements))})))

(defn parse-kml-results
  "Takes kml as a string and returns expected items which will contain a name and a list of shapes"
  [kml-string]
  (map placemark-elem->item (cx/elements-at-path (fx/parse-str kml-string) [:Document :Placemark])))

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

(defn- name+shapes->expected-kml
  "Takes an item name and a list of cmr spatial shapes and returns the list of expected kml
  parsed placemarks."
  [item-name shapes]
  (if (seq shapes)
    (let [shapes-by-coord-sys (group-by #(or (r/coordinate-system %) :geodetic) shapes)
          multiple-placemarks? (> (count shapes-by-coord-sys) 1)]
      (for [[coord-sys shapes] shapes-by-coord-sys]
        {:name (if multiple-placemarks?
                 (str item-name "_" (name coord-sys))
                 item-name)
         :shapes (set (map shape->kml-representation shapes))}))
    [{:name item-name}]))

(defn collection->expected-kml
  [collection]
  (name+shapes->expected-kml
    (:entry-title collection)
    (get-in collection [:spatial-coverage :geometries])))

(defn collections->expected-kml
  [collections]
  {:status 200
   :results (set (mapcat collection->expected-kml collections))})

(defn granule->expected-kml
  [granule coll]
  (name+shapes->expected-kml
    (:granule-ur granule)
    (concat (get-in granule [:spatial-coverage :geometries])
            (dg/granule->orbit-shapes granule coll))))

(defn granules->expected-kml
  [granules collections]
  {:status 200
   :results (set (apply concat (map granule->expected-kml granules collections)))})

(defn- update-result-shapes
  "Returns the result with shapes in a set if applicable"
  [result]
  (if-let [shapes (:shapes result)]
    (update-in result [:shapes] #(when % (set %)))
    result))

(defn- results-for-comparison
  "Returns the results that is suitable for comparison"
  [results]
  (-> results
      (util/update-in-each [:results] update-result-shapes)
      (update-in [:results] set)))

(defn assert-collection-kml-results-match
  "Returns true if the kml results are for the expected items"
  [collections actual-result]
  (clojure.test/is (= (collections->expected-kml collections)
                      (results-for-comparison actual-result))))

(defn assert-granule-kml-results-match
  "Returns true if the kml results are for the expected items"
  [expected-granules collections actual-result]
  (clojure.test/is (= (granules->expected-kml expected-granules collections)
                      (results-for-comparison actual-result))))

(comment

  (def sample-kml (slurp "/Users/jgilman/Desktop/sample_cmr.kml"))

  (parse-kml-results sample-kml)

  (def placemarks
    (cx/elements-at-path (fx/parse-str sample-kml) [:Document :Placemark]))

  (first placemarks)

  (some-> (cx/element-at-path p [:MultiGeometry]))

  (-> p :content))
