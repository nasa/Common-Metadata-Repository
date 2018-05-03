(ns cmr.opendap.ous.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.util :as util]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants/Default Values   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-lon-lo -180.0)
(def default-lon-hi 180.0)
(def default-lat-lo -90.0)
(def default-lat-hi 90.0)

(def default-x-lo 0.0)
(def default-x-hi 360.0)
(def default-y-lo 0.0)
(def default-y-hi 180.0)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Dimensions [x y])
(defrecord ArrayLookup [low high])

;; XXX There is a note in Abdul's code along these lines:
;;         "hack for descending orbits (array index 0 is at 90 degrees
;;         north)"
;;     If I understand correctly, this would cause the indices for
;;     high and low values for latitude to be reversed ... so we
;;     reverse them here, where all OPeNDAP coords get created. This
;;     enables proper lookup in OPeNDAP arrays.
;;
;;     This REALLY needs to be investigated, though.
(defn create-opendap-lookup
  [lon-lo lat-lo lon-hi lat-hi]
  (map->ArrayLookup
   {:low {:x lon-lo
          :y lat-hi} ;; <-- swap hi for lo
    :high {:x lon-hi
           :y lat-lo}})) ;; <-- swap lo for hi

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn dominant-bounds
  "This function is intended to be used if spatial subsetting is not
  proivded in the query: in that case, all the bounds of all the variables
  will be counted, and the one most-used is what will be returned."
  [bounding-info]
  (->> bounding-info
       (map :bounds)
       util/most-frequent))

(defn dominant-dimensions
  "Get the most common dimensions from the bounding-info."
  [bounding-info]
  (->> bounding-info
       (map :dimensions)
       util/most-frequent))

;; XXX Can we use these instead? Why was the phase shifting written
;;     so obtrusely? There's got to be a reason, I just don't know it ...
(defn new-lon-phase-shift
  [x-dim in]
    (int (Math/floor (+ (/ x-dim 2) in))))

(defn new-lat-phase-shift
  [y-dim in]
  (- y-dim
     (int (Math/floor (+ (/ y-dim 2) in)))))

(defn lon-lo-phase-shift
  [x-dim lon-lo]
  (-> (/ (* (- x-dim 1)
            (- lon-lo default-lon-lo))
         (- default-lon-hi default-lon-lo))
      Math/floor
      int))

(defn lon-hi-phase-shift
  [x-dim lon-hi]
  (-> (/ (* (- x-dim 1)
            (- lon-hi default-lon-lo))
         (- default-lon-hi default-lon-lo))
      Math/ceil
      int))

;; XXX Note that the following two functions were copied from this JS:
;;
;; var lats = value.replace("lat(","").replace(")","").split(",");
;; //hack for descending orbits (array index 0 is at 90 degrees north)
;; y_array_end = YDim - 1 - Math.floor((YDim-1)*(lats[0]-lat_begin)/(lat_end-lat_begin));
;; y_array_begin = YDim -1 - Math.ceil((YDim-1)*(lats[1]-lat_begin)/(lat_end-lat_begin));
;;
;; Note the "hack" JS comment ...
;;;
;; This is complicated by the fact that, immediately before those lines of
;; code are a conflicting set of lines overrwitten by the ones pasted above:
;;
;; y_array_begin = Math.floor((YDim-1)*(lats[0]-lat_begin)/(lat_end-lat_begin));
;; y_array_end = Math.ceil((YDim-1)*(lats[1]-lat_begin)/(lat_end-lat_begin));
;;
;; These original JS functions are re-created in Clojure here:

(defn orig-lat-lo-phase-shift
  [y-dim lat-lo]
  (-> (/ (* (- y-dim 1)
            (- lat-lo default-lat-lo))
          (- default-lat-hi default-lat-lo))
       Math/floor
       int))

(defn orig-lat-hi-phase-shift
  [y-dim lat-hi]
  (-> (/ (* (- y-dim 1)
            (- lat-hi default-lat-lo))
          (- default-lat-hi default-lat-lo))
       Math/ceil
       int))

(defn lat-lo-phase-shift
  [y-dim lat-lo]
  (int
    (- y-dim
       1
       (Math/floor (/ (* (- y-dim 1)
                         (- lat-lo default-lat-lo))
                      (- default-lat-hi default-lat-lo))))))

(defn lat-hi-phase-shift
  [y-dim lat-hi]
  (int
    (- y-dim
       1
       (Math/ceil (/ (* (- y-dim 1)
                        (- lat-hi default-lat-lo))
                     (- default-lat-hi default-lat-lo))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-query
  [passed-vars default-vars]
  (string/join "&" (map #(str (codec/url-encode "concept_id[]")
                              "=" %)
                        (if (seq passed-vars)
                            passed-vars
                            default-vars))))

(defn get-metadata
  "Given a 'params' data structure with a ':variables' key (which may or may
  not have values) and a list of all collection variable-ids, return the
  metadata for the passed variables, if defined, and for all associated
  variables, if params does not contain any."
  [search-endpoint user-token params variable-ids]
  (log/debug "Getting variable metadata for:" variable-ids)
  (let [url (str search-endpoint
                 "/variables?"
                 (build-query (:variables params) variable-ids))
        results (request/async-get url
                 (-> {}
                     (request/add-token-header user-token)
                     (request/add-accept "application/vnd.nasa.cmr.umm+json"))
                 response/json-handler)]
    (log/debug "Got results from CMR variable search:" results)
    (:items @results)))

(defn parse-dimensions
  [dim]
  [(:Size (first (filter #(= "XDim" (:Name %)) dim)))
   (:Size (first (filter #(= "YDim" (:Name %)) dim)))])

(defn extract-dimensions
  [entry]
  (->> (get-in entry [:umm :Dimensions])
       (parse-dimensions)
       (apply ->Dimensions)))

(defn parse-annotated-bounds
  "Parse bounds that are annotated with Lat and Lon, returning values
  in the same order that CMR uses for spatial bounding boxes."
  [bounds]
  (let [lon-regex "Lon:\\s*(-?[0-9]+),\\s*(-?[0-9]+).*;\\s*"
        lat-regex "Lat:\\s*(-[0-9]+),\\s*(-?[0-9]+).*"
        [lon-lo lon-hi lat-lo lat-hi]
         (rest (re-find (re-pattern (str lon-regex lat-regex)) bounds))]
    [lon-lo lat-lo lon-hi lat-hi]))

(defn parse-cmr-bounds
  [bounds]
  "Parse a list of lat/lon values ordered according to the CMR convention
  of lower-left lon, lower-left lat, upper-right long, upper-right lat."
  (map string/trim (string/split bounds #",\s*")))

(defn parse-bounds
  [bounds]
  (if (string/starts-with? bounds "Lon")
    (parse-annotated-bounds bounds)
    (parse-cmr-bounds bounds)))

(defn extract-bounds
  [entry]
  (->> entry
       (#(get-in % [:umm :Characteristics :Bounds]))
       parse-bounds
       (map #(Float/parseFloat %))))

(defn create-opendap-bounds
  ([bounding-box]
    (if bounding-box
      (create-opendap-bounds
        {:x default-x-hi :y default-y-hi}
        bounding-box)
      nil))
  ([{x-dim :x y-dim :y} [lon-lo lat-lo lon-hi lat-hi]]
   (let [x-lo (lon-lo-phase-shift x-dim lon-lo)
         x-hi (lon-hi-phase-shift x-dim lon-hi)
         y-lo (lat-lo-phase-shift y-dim lat-lo)
         y-hi (lat-hi-phase-shift y-dim lat-hi)]
     (create-opendap-lookup x-lo y-lo x-hi y-hi))))

(defn format-opendap-lat
  [opendap-bounds]
  (if opendap-bounds
    (format "[%s:%s]"
             (get-in opendap-bounds [:low :y])
             (get-in opendap-bounds [:high :y]))
    ""))

(defn format-opendap-lon
  [opendap-bounds]
  (if opendap-bounds
    (format "[%s:%s]"
             (get-in opendap-bounds [:low :x])
             (get-in opendap-bounds [:high :x]))
    ""))

(defn format-opendap-var-lat-lon
  [opendap-bounds]
  (if opendap-bounds
    (format "[*]%s%s"
      (format-opendap-lat opendap-bounds)
      (format-opendap-lon opendap-bounds))
    ""))

(defn format-opendap-bounds
  ([opendap-bounds]
   (format "Latitude%s,Longitude%s"
           (format-opendap-lat opendap-bounds)
           (format-opendap-lon opendap-bounds)))
  ([bound-name opendap-bounds]
   (format "%s%s"
            bound-name
            (format-opendap-var-lat-lon opendap-bounds))))

(defn extract-bounding-info
  "This function is executed at the variable level, however it has general,
  non-variable-specific bounding info passed to it in order to support
  spatial subsetting"
  [entry bounding-box]
  (let [dims (extract-dimensions entry)
        bounds (or bounding-box (extract-bounds entry))]
    {:concept-id (get-in entry [:meta :concept-id])
     :name (get-in entry [:umm :Name])
     :dimensions dims
     :bounds bounds
     :opendap (create-opendap-bounds dims bounds)
     :size (get-in entry [:umm :Characteristics :Size])}))
