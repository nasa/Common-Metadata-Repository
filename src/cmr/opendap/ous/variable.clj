(ns cmr.opendap.ous.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.errors :as errors]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.ous.query.results :as results]
   [cmr.opendap.util :as util]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Notes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Notes on representing spatial extents.
;;
;; EDSC uses URL-encoded long/lat numbers representing a bounding box
;; Note that the ordering is the same as that used by CMR (see below).
;;  `-9.984375%2C56.109375%2C19.828125%2C67.640625`
;; which URL-decodes to:
;;  `-9.984375,56.109375,19.828125,67.640625`
;;
;; OPeNDAP download URLs have something I haven't figured out yet; given that
;; one of the numbers if over 180, it can't be degrees ... it might be what
;; WCS uses for `x` and `y`?
;;  `Latitude[22:34],Longitude[169:200]`
;;
;; The OUS Prototype uses the WCS standard for lat/long:
;;  `SUBSET=axis[,crs](low,high)`
;; For lat/long this takes the following form:
;;  `subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)`
;;
;; CMR supports bounding spatial extents by describing a rectangle using four
;; comma-separated values:
;;  1. lower left longitude
;;  2. lower left latitude
;;  3. upper right longitude
;;  4. upper right latitude
;; For example:
;;  `bounding_box==-9.984375,56.109375,19.828125,67.640625`
;;
;; Google's APIs use lower left, upper right, but the specify lat first, then
;; long:
;;  `southWest = LatLng(56.109375,-9.984375);`
;;  `northEast = LatLng(67.640625,19.828125);`

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants/Default Values   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-lon-lo -180.0)
(def default-lon-hi 180.0)
(def default-lat-lo -90.0)
(def default-lat-hi 90.0)

(def default-lon-abs-lo 0.0)
(def default-lon-abs-hi 360.0)
(def default-lat-abs-lo 0.0)
(def default-lat-abs-hi 180.0)

(def default-stride 1)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; We're going to codify parameters with records to keep things well
;;; documented. Additionally, this will make converting between parameter
;;; schemes an explicit operation on explicit data.

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

;; XXX Can we use these instead? Why was the phase shifting written
;;     so obtrusely? There's got to be a reason, I just don't know it ...
;; XXX This is being tracked in CMR-4959
(defn new-lon-phase-shift
  [x-dim in]
    (int (Math/floor (+ (/ x-dim 2) in))))

(defn new-lat-phase-shift
  [y-dim in]
  (- y-dim
     (int (Math/floor (+ (/ y-dim 2) in)))))

;; The following longitudinal phase shift functions are translated from the
;; OUS Node.js prototype. It would be nice to use the more general functions
;; above, if those work out.

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
;;
;; This is complicated by the fact that, immediately before those lines of
;; code are a conflicting set of lines overrwitten by the ones pasted above:
;;
;; y_array_begin = Math.floor((YDim-1)*(lats[0]-lat_begin)/(lat_end-lat_begin));
;; y_array_end = Math.ceil((YDim-1)*(lats[1]-lat_begin)/(lat_end-lat_begin));
;;
;; Even though this code was ported to Clojure, it was problematic ... very likely
;; due to the fact that there were errors in the source data (XDim/YDim were
;; swapped) and the original JS code didn't acknowledge that fact. There is every
;; possibility that we can delete the following functions.
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

;; The following latitudinal phase shift functions are what is currently being
;; used.

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
  [variable-ids]
  (string/join
   "&"
   (conj
    (map #(str (codec/url-encode "concept_id[]")
               "=" %)
         variable-ids)
    (str "page_size=" (count variable-ids)))))

(defn async-get-metadata
  "Given a 'params' data structure with a ':variables' key (which may or may
  not have values) and a list of all collection variable-ids, return the
  metadata for the passed variables, if defined, and for all associated
  variables, if params does not contain any."
  [search-endpoint user-token {variable-ids :variables}]
  (if (seq variable-ids)
    (let [url (str search-endpoint "/variables")
          payload (build-query variable-ids)]
      (log/debug "Variables query CMR URL:" url)
      (log/debug "Variables query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept "application/vnd.nasa.cmr.umm+json")
           (request/add-form-ct)
           (request/add-payload payload))
       response/json-handler))
    (deliver (promise) [])))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error errors/variable-metadata)
        rslts)
      (do
        (log/debug "Got results from CMR variable search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (:items rslts)))))

(defn get-metadata
  [search-endpoint user-token variables]
  (let [promise (async-get-metadata search-endpoint user-token variables)]
    (extract-metadata promise)))

(defn parse-dimensions
  [dim]
  ;; XXX It seems that the X and Y have been swapped for at least
  ;;     on collection's variables; Simon and I are looking into this
  ;;     for now, we're just gonna pretend ... by changing the order
  ;;     below :-(
  ;; XXX This is being tracked in CMR-4958
  [(or (:Size (first (filter #(= "YDim" (:Name %)) dim))) default-lon-abs-hi)
   (or (:Size (first (filter #(= "XDim" (:Name %)) dim))) default-lat-abs-hi)])

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
  (if entry
    (->> entry
         (#(get-in % [:umm :Characteristics :Bounds]))
         parse-bounds
         (map #(Float/parseFloat %)))
    nil))

(defn create-opendap-bounds
  ([bounding-box]
   (create-opendap-bounds {:x default-lon-abs-hi :y default-lat-abs-hi} bounding-box))
  ([{x-dim :x y-dim :y} [lon-lo lat-lo lon-hi lat-hi :as bounding-box]]
   (if bounding-box
     (let [x-lo (lon-lo-phase-shift x-dim lon-lo)
           x-hi (lon-hi-phase-shift x-dim lon-hi)
           y-lo (lat-lo-phase-shift y-dim lat-lo)
           y-hi (lat-hi-phase-shift y-dim lat-hi)]
       (create-opendap-lookup x-lo y-lo x-hi y-hi))
     nil)))

(defn format-opendap-lat
  ([opendap-bounds]
   (format-opendap-lat opendap-bounds default-stride))
  ([opendap-bounds stride]
   (if opendap-bounds
     (format "[%s:%s:%s]"
              (get-in opendap-bounds [:low :y])
              stride
              (get-in opendap-bounds [:high :y]))
     "")))

(defn format-opendap-lon
  ([opendap-bounds]
   (format-opendap-lon opendap-bounds default-stride))
  ([opendap-bounds stride]
   (if opendap-bounds
     (format "[%s:%s:%s]"
              (get-in opendap-bounds [:low :x])
              stride
              (get-in opendap-bounds [:high :x]))
    "")))

(defn format-opendap-var-lat-lon
  ([opendap-bounds]
   (format-opendap-var-lat-lon opendap-bounds default-stride))
  ([opendap-bounds stride]
   (if opendap-bounds
     (format "[*]%s%s"
       (format-opendap-lat opendap-bounds stride)
       (format-opendap-lon opendap-bounds stride))
     "")))

(defn format-opendap-lat-lon
  ([opendap-bounds]
   (format-opendap-lat-lon opendap-bounds default-stride))
  ([opendap-bounds stride]
   (format "Latitude%s,Longitude%s"
           (format-opendap-lat opendap-bounds stride)
           (format-opendap-lon opendap-bounds stride))))

(defn format-opendap-bounds
  ([bound-name opendap-bounds]
   (format-opendap-bounds bound-name opendap-bounds default-stride))
  ([bound-name opendap-bounds stride]
   (format "%s%s"
            bound-name
            (format-opendap-var-lat-lon opendap-bounds stride))))

(defn extract-bounding-info
  "This function is executed at the variable level, however it has general,
  non-variable-specific bounding info passed to it in order to support
  spatial subsetting"
  [entry bounding-box]
  (log/debug "Got variable entry:" entry)
  (log/debug "Got bounding-box:" bounding-box)
  (let [dims (extract-dimensions entry)
        ;; XXX Once we sort out how to definitely extract lat/lon and
        ;;     whether there is ever a need to go to
        ;;     :umm :Characteristics :Bounds when we can just go to
        ;;     :umm :Dimensions instead, we can come back to this code
        ;;     and remove the following line or integrate it into the
        ;;     code.
        ;; XXX This is being tracked as part of CMR-4922 and CMR-4958
        ; bounds (or bounding-box (extract-bounds entry))
        ]
    {:concept-id (get-in entry [:meta :concept-id])
     :name (get-in entry [:umm :Name])
     :dimensions dims
     :bounds bounding-box
     :opendap (create-opendap-bounds dims bounding-box)
     :size (get-in entry [:umm :Characteristics :Size])}))


