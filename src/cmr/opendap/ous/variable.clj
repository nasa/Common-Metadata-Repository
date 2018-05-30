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

(def default-dim-stride 1)
(def default-lat-lon-stride 1)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Records   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; We're going to codify parameters with records to keep things well
;;; documented. Additionally, this will make converting between parameter
;;; schemes an explicit operation on explicit data.

(defrecord Point [lon lat])

(defrecord ArrayLookup [low high])

(defrecord BoundingInfo
  [;; :meta :concept-id
   concept-id
   ;; :umm :Name
   name
   ;; :umm :Dimensions, converted to EDN
   dimensions
   ;; Bounding box data from query params
   bounds
   ;; OPeNDAP lookup array
   opendap
   ;; :umm :Characteristics :Size
   size])

;; XXX There is a note in Abdul's code along these lines:
;;         "hack for descending orbits (array index 0 is at 90 degrees
;;         north)"
;;     If I understand correctly, this would cause the indices for
;;     high and low values for latitude to be reversed ... so we
;;     reverse them here, where all OPeNDAP coords get created. This
;;     enables proper lookup in OPeNDAP arrays.
;;
;;     This REALLY needs to be investigated, though, to make sure the
;;     understanding is correct. And then it needs to be DOCUMENTED.
;;
;; XXX This is being tracked in CMR-4963
(defn create-opendap-lookup
  [lon-lo lat-lo lon-hi lat-hi]
  (map->ArrayLookup
   {:low {:lon lon-lo
          :lat lat-hi} ;; <-- swap hi for lo; see note above
    :high {:lon lon-hi
           :lat lat-lo}})) ;; <-- swap lo for hi; see note above

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX Can we use these instead? Why was the phase shifting written
;;     so obtrusely? There's got to be a reason, I just don't know it ...
;; XXX This is being tracked in CMR-4959
(defn new-lon-phase-shift
  [lon-dim in]
    (int (Math/floor (+ (/ lon-dim 2) in))))

(defn new-lat-phase-shift
  [lat-dim in]
  (- lat-dim
     (int (Math/floor (+ (/ lat-dim 2) in)))))

;; The following longitudinal phase shift functions are translated from the
;; OUS Node.js prototype. It would be nice to use the more general functions
;; above, if those work out.

(defn lon-lo-phase-shift
  [lon-dim lon-lo]
  (-> (/ (* (- lon-dim 1)
            (- lon-lo default-lon-lo))
         (- default-lon-hi default-lon-lo))
      Math/floor
      int))

(defn lon-hi-phase-shift
  [lon-dim lon-hi]
  (-> (/ (* (- lon-dim 1)
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
  [lat-dim lat-lo]
  (-> (/ (* (- lat-dim 1)
            (- lat-lo default-lat-lo))
          (- default-lat-hi default-lat-lo))
       Math/floor
       int))

(defn orig-lat-hi-phase-shift
  [lat-dim lat-hi]
  (-> (/ (* (- lat-dim 1)
            (- lat-hi default-lat-lo))
          (- default-lat-hi default-lat-lo))
       Math/ceil
       int))

;; The following latitudinal phase shift functions are what is currently being
;; used.

(defn lat-lo-phase-shift
  [lat-dim lat-lo]
  (int
    (- lat-dim
       1
       (Math/floor (/ (* (- lat-dim 1)
                         (- lat-lo default-lat-lo))
                      (- default-lat-hi default-lat-lo))))))

(defn lat-hi-phase-shift
  [lat-dim lat-hi]
  (int
    (- lat-dim
       1
       (Math/ceil (/ (* (- lat-dim 1)
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

(defn parse-lat-lon
  [dim]
  [(or (:Size (first (filter #(= "Longitude" (:Name %)) dim)))
       (:Size (first (filter #(= "XDim" (:Name %)) dim)))
       default-lon-abs-hi)
   (or (:Size (first (filter #(= "Latitude" (:Name %)) dim)))
       (:Size (first (filter #(= "YDim" (:Name %)) dim)))
       default-lat-abs-hi)])

(defn extract-dimensions
  [entry]
  (->> (get-in entry [:umm :Dimensions])
       (map #(vector (keyword (:Name %)) (:Size %)))
       (into (array-map))))

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
   (create-opendap-bounds {:Longitude default-lon-abs-hi
                           :Latitude default-lat-abs-hi} bounding-box))
  ([{lon-dim :Longitude lat-dim :Latitude :as _dimensions}
    [lon-lo lat-lo lon-hi lat-hi :as bounding-box]]
   (if bounding-box
     (let [lon-lo (lon-lo-phase-shift lon-dim lon-lo)
           lon-hi (lon-hi-phase-shift lon-dim lon-hi)
           lat-lo (lat-lo-phase-shift lat-dim lat-lo)
           lat-hi (lat-hi-phase-shift lat-dim lat-hi)]
       (create-opendap-lookup lon-lo lat-lo lon-hi lat-hi))
     nil)))

(defn format-opendap-dim
  [min stride max]
  (format "[%s:%s:%s]" min stride max))

(defn format-opendap-dim-lat
  ([bounding-info]
   (format-opendap-dim-lat bounding-info default-lat-lon-stride))
  ([bounding-info stride]
   (if-let [opendap-bounds (:opendap bounding-info)]
     (format-opendap-dim (get-in opendap-bounds [:low :lat])
                         stride
                         (get-in opendap-bounds [:high :lat]))
     "")))

(defn format-opendap-dim-lon
  ([bounding-info]
   (format-opendap-dim-lon bounding-info default-lat-lon-stride))
  ([bounding-info stride]
   (if-let [opendap-bounds (:opendap bounding-info)]
     (format-opendap-dim (get-in opendap-bounds [:low :lon])
                         stride
                         (get-in opendap-bounds [:high :lon]))
    "")))

(defn replace-defaults-lat-lon
  [bounding-info stride [k v]]
  (cond (= k :Longitude) (format-opendap-dim-lon bounding-info stride)
        (= k :Latitude) (format-opendap-dim-lat bounding-info stride)
        :else (format-opendap-dim 0 stride (dec v))))

(defn format-opendap-dims
  ([bounding-info]
    (format-opendap-dims bounding-info default-dim-stride))
  ([bounding-info stride]
    (->> bounding-info
         :dimensions
         (map (partial replace-defaults-lat-lon bounding-info stride))
         (apply str))))

(defn format-opendap-var-dims-lat-lon
  ([bounding-info]
   (format-opendap-var-dims-lat-lon bounding-info default-lat-lon-stride))
  ([bounding-info stride]
   (if (:opendap bounding-info)
     (format-opendap-dims bounding-info)
     "")))

(defn format-opendap-lat-lon
  ([bounding-info]
   (format-opendap-lat-lon bounding-info default-lat-lon-stride))
  ([bounding-info stride]
   (format "Latitude%s,Longitude%s"
           (format-opendap-dim-lat bounding-info stride)
           (format-opendap-dim-lon bounding-info stride))))

(defn format-opendap-bounds
  ([bounding-info]
   (format-opendap-bounds bounding-info default-lat-lon-stride))
  ([{bound-name :name :as bounding-info} stride]
   (format "%s%s"
            bound-name
            (format-opendap-var-dims-lat-lon bounding-info stride))))

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
        ;;     :umm :Point instead, we can come back to this code
        ;;     and remove the following line or integrate it into the
        ;;     code.
        ;; XXX This is being tracked as part of CMR-4922 and CMR-4958
        ; bounds (or bounding-box (extract-bounds entry))
        ]
    (map->BoundingInfo
      {:concept-id (get-in entry [:meta :concept-id])
       :name (get-in entry [:umm :Name])
       :dimensions dims
       :bounds bounding-box
       :opendap (create-opendap-bounds dims bounding-box)
       :size (get-in entry [:umm :Characteristics :Size])})))


