(ns cmr.umm-spec.umm-g.spatial
  "Contains functions for parsing UMM-G JSON SpatialExtent into umm-lib granule model
  SpatialCoverage and generating UMM-G JSON SpatialExtent from umm-lib granule model SpatialCoverage."
  (:require
   [cmr.spatial.cartesian-ring]
   [cmr.spatial.geodetic-ring]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as point]
   [cmr.spatial.polygon :as poly]
   [cmr.umm-spec.umm-g.track :as track]
   [cmr.umm.umm-granule :as g]
   [cmr.umm.umm-spatial :as umm-s])
  (:import cmr.umm.umm_granule.UmmGranule))

(defn- umm-g-point->Point
  "Returns the spatial point from the given UMM-G Point."
  [point]
  (point/point (:Longitude point) (:Latitude point)))

(defn- umm-g-bounding-ractangle->BoundingRectangle
  "Returns the spatial BoundingRectangle from the given UMM-G BoundingRectangle."
  [bounding-ractangle]
  (let [west (:WestBoundingCoordinate bounding-ractangle)
        east (:EastBoundingCoordinate bounding-ractangle)
        north (:NorthBoundingCoordinate bounding-ractangle)
        south (:SouthBoundingCoordinate bounding-ractangle)]
    (mbr/mbr west north east south)))

(defn- umm-g-boundary->Ring
  [boundary]
  (umm-s/ring (map umm-g-point->Point (:Points boundary))))

(defn- umm-g-gpolygon->GPolygon
  "Returns the spatial polygon from the given UMM-G GPolygon."
  [gpolygon]
  [gpolygon]
  (let [outer-ring (umm-g-boundary->Ring (:Boundary gpolygon))
        holes (map umm-g-boundary->Ring (get-in gpolygon [:ExclusiveZone :Boundaries]))]
    (poly/polygon (cons outer-ring holes))))

(defn- umm-g-line->Line
  "Returns the spatial Line from the given UMM-G Line"
  [line]
  (l/line-string (map umm-g-point->Point (:Points line))))

(defn- umm-g-geometry->geometries
  "Returns umm-lib geometries from the given UMM-G Geometry."
  [geometry]
  (let [{:keys [Points BoundingRectangles GPolygons Lines]} geometry]
    (remove nil?
            (concat (map umm-g-point->Point Points)
                    (map umm-g-bounding-ractangle->BoundingRectangle BoundingRectangles)
                    (map umm-g-gpolygon->GPolygon GPolygons)
                    (map umm-g-line->Line Lines)))))

(def key->orbit-direction
  "Mapping of keys to orbit direction stirngs."
  {:asc "A"
   :desc "D"})

(def orbit-direction->key
  "Mapping of oribit direction strings to keywords."
  {"A" :asc
   "D" :desc})

(defn- umm-g-orbit->Orbit
  "Returns the UMM Orbit record from the given UMM Orbit."
  [orbit]
  (g/map->Orbit {:ascending-crossing (:AscendingCrossing orbit)
                 :start-lat (:StartLatitude orbit)
                 :start-direction (orbit-direction->key (:StartDirection orbit))
                 :end-lat (:EndLatitude orbit)
                 :end-direction (orbit-direction->key (:EndDirection orbit))}))

(defn umm-g-spatial-extent->SpatialCoverage
  "Returns the umm-lib granule model SpatialCoverage from the given UMM-G granule."
  [umm-g-json]
  (let [spatial (get-in umm-g-json [:SpatialExtent :HorizontalSpatialDomain])
        geometry (:Geometry spatial)
        orbit (:Orbit spatial)
        track (:Track spatial)]
    (when (or geometry orbit)
      (g/map->SpatialCoverage
       {:geometries (when geometry (umm-g-geometry->geometries geometry))
        :orbit (when orbit (umm-g-orbit->Orbit orbit))
        :track (track/umm-g-track->Track track)}))))

(defn- Point->umm-g-point
  "Returns the UMM-G Point from the given spatial point."
  [point]
  {:Longitude (.lon point)
   :Latitude (.lat point)})

(defn- BoundingRectangle->umm-g-bounding-ractangle
  "Returns the UMM-G BoundingRectangle from the given spatial BoundingRectangle."
  [bounding-ractangle]
  (let [west (.west bounding-ractangle)
        east (.east bounding-ractangle)
        north (.north bounding-ractangle)
        south (.south bounding-ractangle)]
    {:WestBoundingCoordinate west
     :EastBoundingCoordinate east
     :NorthBoundingCoordinate north
     :SouthBoundingCoordinate south}))

(defn- Ring->umm-g-boundary
  "Returns the UMM-G Boundary for the given spatial ring."
  [ring]
  {:Points (->> ring
                umm-s/ring->lat-lon-point-str
                umm-s/lat-lon-point-str->points
                (map Point->umm-g-point))})

(defn- GPolygon->umm-g-gpolygon
  "Returns the UMM-G GPolygon from the given spatial polygon."
  [gpolygon]
  (let [outer-ring (poly/boundary gpolygon)
        holes (poly/holes gpolygon)]
    (merge
     {:Boundary (Ring->umm-g-boundary outer-ring)}
     (when (seq holes)
       {:ExclusiveZone {:Boundaries (map Ring->umm-g-boundary holes)}}))))

(defn- Line->umm-g-line
  "Returns the UMM-G Line from the given spatial Line."
  [line]
  {:Points (map Point->umm-g-point (.points line))})

(defn SpatialCoverage->umm-g-spatial-extent
  "Returns the UMM-G SpatialExtent from the given umm-lib granule model SpatialCoverage."
  [spatial]
  (when spatial
    (let [{:keys [geometries orbit track]} spatial]
      {:HorizontalSpatialDomain
       {:Geometry
        (when (seq geometries)
          (let [geometry-by-type (group-by type geometries)
                points (get geometry-by-type cmr.spatial.point.Point)
                bounding-rectangles (get geometry-by-type cmr.spatial.mbr.Mbr)
                gpolygons (get geometry-by-type cmr.spatial.polygon.Polygon)
                lines (get geometry-by-type cmr.spatial.line_string.LineString)]
            (merge
             (when (seq points)
               {:Points (map Point->umm-g-point points)})
             (when (seq bounding-rectangles)
               {:BoundingRectangles (map BoundingRectangle->umm-g-bounding-ractangle bounding-rectangles)})
             (when (seq gpolygons)
               {:GPolygons (map GPolygon->umm-g-gpolygon gpolygons)})
             (when (seq lines)
               {:Lines (map Line->umm-g-line lines)}))))
        :Orbit (when orbit
                 (let [{:keys [ascending-crossing start-lat start-direction
                               end-lat end-direction]} orbit]
                   {:AscendingCrossing ascending-crossing
                    :StartLatitude start-lat
                    :StartDirection (key->orbit-direction start-direction)
                    :EndLatitude end-lat
                    :EndDirection (key->orbit-direction end-direction)}))
        :Track (track/Track->umm-g-track track)}})))
