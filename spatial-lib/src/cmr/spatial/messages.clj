(ns cmr.spatial.messages
  "Contains error messages for spatial validation."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.util :as u]
   [pjstadig.assertions :as pj]))

(defconfig max-line-points
  "The maximum number of points a line parameter can have"
  {:default 500 :type Long})

(defn line-too-many-points-msg
  [type s]
  (format "[%s] has too many points for type %s" s (csk/->snake_case_string type)))

(defn shape-decode-msg
  [type s]
  (format "[%s] is not a valid URL encoded %s" s (csk/->snake_case_string type)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point validation messages

(defn point-lon-invalid
  [lon]
  (format "Point longitude [%s] must be within -180.0 and 180.0"
          (u/double->string lon)))

(defn point-lat-invalid
  [lat]
  (format "Point latitude [%s] must be within -90 and 90.0"
          (u/double->string lat)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Shapes with points validation messages

(defn shape-point-invalid
  [point-index point-error]
  (format "Point %d had the following error: %s"
          (inc point-index)
          point-error))

(defn- point->human-readable
  "Converts a point into a human readable representation"
  [{:keys [lon lat]}]
  (format "[lon=%s lat=%s]" (u/double->string lon) (u/double->string lat)))

(defn- indexed-point->-msg-part
  "Takes an index and point pair and returns a human readable representation"
  [[i point]]
  (format "%d %s" (inc i) (point->human-readable point)))

(defn duplicate-points
  "Takes a list of index point pairs that were considered duplicates or very close."
  [points-w-index]
  (pj/assert (>= (count points-w-index) 2))

  (let [point-msg-parts (map indexed-point->-msg-part points-w-index)]
    (format (str "The shape contained duplicate points. "
                 "Points %s and %s were considered equivalent or very close.")
            (str/join ", " (drop-last point-msg-parts))
            (last point-msg-parts))))

(defn consecutive-antipodal-points
  "Takes a list of index point pairs that were considered consecutive antipodal points."
  [point1-w-index point2-w-index]
  (format (str "The shape contained consecutive antipodal points. "
               "Points %s and %s are antipodal.")
          (indexed-point->-msg-part point1-w-index)
          (indexed-point->-msg-part point2-w-index)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ring validation messages

(defn ring-not-closed
  []
  "Polygon boundary was not closed. The last point must be equal to the first point.")

(defn ring-self-intersections
  "Takes a list of points where the ring intersects itself"
  [intersections]
  (format "The polygon boundary intersected itself at the following points: %s"
          (str/join "," (map point->human-readable intersections))))

(defn ring-contains-both-poles
  []
 "The polygon boundary contains both the North and South Poles. A polygon can contain at most one pole.")

(defn ring-points-out-of-order
  []
  "The polygon boundary points are listed in the wrong order. Points must be provided in counter-clockwise order.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Polygon validation messages

(defn hole-not-covered-by-boundary
  [hole-index]
  (format "The polygon boundary does not completely cover hole number %d." (inc hole-index)))

(defn hole-intersects-hole
  [hole1-index hole2-index]
  (format "The polygon hole number %d intersects hole number %d." (inc hole1-index) (inc hole2-index)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bounding rectangle validation messages

(defn br-north-less-than-south
  [north south]
  (format "The bounding rectangle north value [%s] was less than the south value [%s]"
          (u/double->string north) (u/double->string south)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Orbit messages

(defn start-end-direction
  [field direction]
  (format "The orbit %s is [%s], must be either A or D." field direction))
