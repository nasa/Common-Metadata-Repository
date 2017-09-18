(ns cmr.umm-spec.xml-to-umm-mappings.dif9.spatial-extent
  "Defines mappings from DIF 9 Spatial_Coverage elements into UMM records"
  (:require
   [clojure.string :as string]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.util :refer [default-granule-spatial-representation]]))

;; Bounding Box Parsing
;; This is the DIF 9 bounding box parsing map
(def bounding-box-parsing-map {:NorthBoundingCoordinate "Northernmost_Latitude"
                               :SouthBoundingCoordinate "Southernmost_Latitude"
                               :WestBoundingCoordinate "Westernmost_Longitude"
                               :EastBoundingCoordinate "Easternmost_Longitude"})

(defn- build-bounding-rectangle
  "This function uses recursion to peice together all of the horizontal domain coordinates into
   a map without nil values. The return value is a map of either the coordinates or nil.
   {:WestBoundingCoordinate \"-180\",
    :NorthBoundingCoordinate \"-90\",
    :EastBoundingCoordinate \"180\",
    :SouthBoundingCoordinate \"90\"}
   Nil values will not exist in the map."
  [spatial-coverage bounding-box-map result]
  (let [keyvalue (first bounding-box-map)
        bounding-key (first keyvalue)
        bounding-value (last keyvalue)]
    (if (= bounding-key :EastBoundingCoordinate)
      (if-let [value (value-of spatial-coverage bounding-value)]
        (if (nil? result)
          (assoc {} bounding-key value)
          (assoc result bounding-key value))
        result)
      (if-let [value (value-of spatial-coverage bounding-value)]
        (let [new-result
               (if (nil? result)
                 (assoc {} bounding-key value)
                 (assoc result bounding-key value))]
          (recur spatial-coverage (drop 1 bounding-box-map) new-result))
        (recur spatial-coverage (drop 1 bounding-box-map) result)))))

(defn- create-bounding-rectangles
  "This function returns a sequence of bounding rectangle maps that do not contain nils.
   If no bounding rectangles exist then it returns an empty sequence. The passed in values are
   the DIF 9 XML and the bounding box map used to parse the XML."
  [doc bounding-box-map]
  (remove nil?
    (for [el (select doc "/DIF/Spatial_Coverage")]
      (build-bounding-rectangle el bounding-box-map nil))))

(defn- parse-horizontal-domain
  "Create the horizontal spatial domain part of the spatial extent."
  [doc]
  (when-let [brs (seq (create-bounding-rectangles doc bounding-box-parsing-map))]
    {:HorizontalSpatialDomain
     {:Geometry {:CoordinateSystem "CARTESIAN" ;; DIF9 doesn't have CoordinateSystem, default to CARTESIAN
                 :BoundingRectangles brs}}}))

;; Vertical Domain parsing
;; This is the list to go through to get all of the DIF 9 elevation elements.
(def elevation-list ["Minimum_Altitude" "Maximum_Altitude" "Minimum_Depth" "Maximum_Depth"])

(defn- create-vertical-domain-obj
  "This method returns a vertical domain map that consists of {:Type type :Value value} if a value exists
  otherwise it returns nil."
  [spatial-coverage elevation-type]
  (if-let [value (value-of spatial-coverage elevation-type)]
    (into {}
       {:Type (string/replace elevation-type "_" " ")
        :Value value})))

(defn- build-vertical-domains
  "This function uses recursion to peice together all of the vertical domain maps into
   a vector without nil values. The return value is a vector of either maps or its empty.
   [{:Type \"Minimum Altitude\", :Value \"0\"}
    {:Type \"Maximum Altitude\", :Value \"100\"}]
   Nil values will not exist in the vector."
  [spatial-coverage elevation-list result]
  (if-let [elevation-type (first elevation-list)]
    (if (= elevation-type "Maximum_Depth")
      (if-let [obj (create-vertical-domain-obj spatial-coverage elevation-type)]
        (conj result obj)
        result)
      (if-let [obj (create-vertical-domain-obj spatial-coverage elevation-type)]
        (let [new-result (conj result obj)]
          (recur spatial-coverage (drop 1 elevation-list) new-result))
        (recur spatial-coverage (drop 1 elevation-list) result)))
    result))

(defn- create-vertical-domains
  "Returns a seq of a vector that contains vertical spatial domains by the given DIF XML.
   The DIF XML is passed in."
 [doc]
 (for [spatial_coverage (select doc "/DIF/Spatial_Coverage")]
   (build-vertical-domains spatial_coverage elevation-list [])))

(defn- parse-vertical-domains
  "Create the vertical spatial domains part of the spatial extent."
  [doc]
  (when-let [vertical-domains (vec (flatten (create-vertical-domains doc)))]
    (if (not-empty vertical-domains)
      {:VerticalSpatialDomains vertical-domains})))

(defn- parse-granule-representation
  "Parse the granule spatial representation"
  [doc sanitize?]
  {:GranuleSpatialRepresentation (or (value-of doc "/DIF/Extended_Metadata/Metadata[Name='GranuleSpatialRepresentation']/Value")
                                     (when sanitize?
                                       default-granule-spatial-representation))})

(defn- merge-spatial-extent
  "Parse and merge the horizontal and vertical domains. Return a partial map of Spatial Extent"
  [doc]
  (let [horizontal-domain (parse-horizontal-domain doc)
        vertical-domain (parse-vertical-domains doc)]
    (when (or horizontal-domain vertical-domain)
      (let [spatial-type (if (and horizontal-domain vertical-domain)
                           "HORIZONTAL_VERTICAL"
                           (if horizontal-domain
                             "HORIZONTAL"
                             "VERTICAL"))]
        (into {}
            [{:SpatialCoverageType spatial-type}
             horizontal-domain
             vertical-domain])))))

(defn parse-spatial-extent
  "Parse the spatial extent from the passed in document. Return the spatial extent record."
  [doc sanitize?]
  (into {}
     [(parse-granule-representation doc sanitize?)
      (merge-spatial-extent doc)]))
