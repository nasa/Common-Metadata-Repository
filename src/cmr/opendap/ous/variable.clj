(ns cmr.opendap.ous.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.const :as const]
   [cmr.opendap.errors :as errors]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.ous.query.results :as results]
   [cmr.opendap.ous.util.geog :as geog]
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

(def default-dim-stride 1)
(def default-lat-lon-stride 1)

;; XXX The following set and function are a hard-coded work-around for the
;;     fact that we don't currently have a mechanism for identifying the
;;     "direction of storage" or "endianness" of latitude data in different
;;     data sets: some store data from -90 to 90N starting at index 0, some
;;     from 90 to -90.
;;
;; XXX This is being tracked in CMR-4982
(def lat-reversed-datasets
  #{"Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"
    "MODIS/Terra Aerosol Cloud Water Vapor Ozone Daily L3 Global 1Deg CMG V006"})

(defn lat-reversed?
  [coll]
  (log/debug "Checking collection for reversed latitudinal values ...")
  (log/trace "Collection data:" coll)
  (let [dataset-id (:dataset_id coll)
        reversed? (contains? lat-reversed-datasets dataset-id)]
    (log/debug "Data set id:" dataset-id)
    (if reversed?
      (log/debug "Identfied data set as having reversed latitude order ...")
      (log/debug "Identfied data set as having normal latitude order ..."))
    ;; XXX coll is required as an arg here because it's needed in a
    ;;     workaround for different data sets using different starting
    ;;     points for their indices in OPeNDAP
    ;;
    ;;     Ideally, we'll have something in a UMM-Var's metadata that
    ;;     will allow us to make the reversed? assessment.
    ;;
    ;; XXX This is being tracked in CMR-4982 and CMR-4896
    reversed?))

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

(defn create-opendap-lookup
  "This lookup is needed for when latitude -90N is stored at the 0th index and
  90N is stored at the highest index (whose actual number will varry, depending
  upon the resolution of the data)."
  [lon-lo lat-lo lon-hi lat-hi]
  (map->ArrayLookup
   {:low {:lon lon-lo
          :lat lat-lo}
    :high {:lon lon-hi
           :lat lat-hi}}))

(defn create-opendap-lookup-reversed
  "This lookup is needed for when latitude 90N is stored at the 0th index and
  -90N is stored at the highest index (whose actual number will varry, depending
  upon the resolution of the data)."
  [lon-lo lat-lo lon-hi lat-hi]
  (let [lookup (create-opendap-lookup lon-lo lat-lo lon-hi lat-hi)
        reversed-hi-lat (get-in lookup [:high :lat])
        reversed-lo-lat (get-in lookup [:low :lat])]
    (-> lookup
        (assoc-in [:low :lat] reversed-hi-lat)
        (assoc-in [:high :lat] reversed-lo-lat))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalize-lat-lon
  [dim]
  (-> dim
      (assoc :Latitude (or (:Latitude dim)
                           (:lat dim)
                           ;; XXX See CMR-4985
                           (:YDim dim))
             :Longitude (or (:Longitude dim)
                            (:lon dim)
                            ;; XXX See CMR-4985
                            (:XDim dim)))
      (dissoc :lat :lon :XDim :YDim)))

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
        (log/trace "Got results from CMR variable search:"
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
       const/default-lon-abs-hi)
   (or (:Size (first (filter #(= "Latitude" (:Name %)) dim)))
       (:Size (first (filter #(= "YDim" (:Name %)) dim)))
       const/default-lat-abs-hi)])

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
  (when entry
    (->> entry
         (#(get-in % [:umm :Characteristics :Bounds]))
         parse-bounds
         (map #(Float/parseFloat %)))))

(defn create-opendap-bounds
  ([bounding-box]
   (create-opendap-bounds bounding-box {:reversed? true}))
  ([bounding-box opts]
   (create-opendap-bounds {:Longitude const/default-lon-abs-hi
                           :Latitude const/default-lat-abs-hi}
                          bounding-box
                          opts))
  ([{lon-max :Longitude lat-max :Latitude :as dimensions}
    [lon-lo lat-lo lon-hi lat-hi :as bounding-box]
    opts]
   (log/trace "Got dimensions:" dimensions)
   (when bounding-box
     (let [lon-lo (geog/lon-lo-phase-shift lon-max lon-lo)
           lon-hi (geog/lon-hi-phase-shift lon-max lon-hi)]
       (if (:reversed? opts)
         (let [lat-lo (geog/lat-lo-phase-shift-reversed lat-max lat-lo)
               lat-hi (geog/lat-hi-phase-shift-reversed lat-max lat-hi)]
           (log/debug "Variable latitudinal values are reversed ...")
           (create-opendap-lookup-reversed lon-lo lat-lo lon-hi lat-hi))
         (let [lat-lo (geog/lat-lo-phase-shift lat-max lat-lo)
               lat-hi (geog/lat-hi-phase-shift lat-max lat-hi)]
           (create-opendap-lookup lon-lo lat-lo lon-hi lat-hi)))))))

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
    (if (:opendap bounding-info)
      (->> bounding-info
           :dimensions
           (map (partial replace-defaults-lat-lon bounding-info stride))
           (apply str))
      "")))

(defn get-lat-lon-format-str
  [bounding-info]
  (log/debug "Original dimensions:" (:original-dimensions bounding-info))
  (str (cond (get-in bounding-info [:original-dimensions :Latitude])
             "Latitude"
             (get-in bounding-info [:original-dimensions :lat])
             "lat"
             ;; XXX See CMR-4985
             (get-in bounding-info [:original-dimensions :YDim])
             "YDim")
       "%s,"
       (cond (get-in bounding-info [:original-dimensions :Longitude])
             "Longitude"
             (get-in bounding-info [:original-dimensions :lon])
             "lon"
             ;; XXX See CMR-4985
             (get-in bounding-info [:original-dimensions :XDim])
             "XDim")
       "%s"))

(defn format-opendap-lat-lon
  ([bounding-info]
   (format-opendap-lat-lon bounding-info default-lat-lon-stride))
  ([bounding-info stride]
   (format (get-lat-lon-format-str bounding-info)
           (format-opendap-dim-lat bounding-info stride)
           (format-opendap-dim-lon bounding-info stride))))

(defn format-opendap-bounds
  ([bounding-info]
   (format-opendap-bounds bounding-info default-lat-lon-stride))
  ([{bound-name :name :as bounding-info} stride]
   (log/trace "Bounding info:" bounding-info)
   (format "%s%s"
            bound-name
            (format-opendap-dims bounding-info stride))))

(defn extract-bounding-info
  "This function is executed at the variable level, however it has general,
  non-variable-specific bounding info passed to it in order to support
  spatial subsetting"
  [coll entry bounding-box]
  ;; XXX coll is required as an arg here because it's needed in a
  ;;     workaround for different data sets using different starting
  ;;     points for their indices in OPeNDAP
  ;;
  ;; XXX This is being tracked in CMR-4982
  (log/trace "Got collection:" coll)
  (log/trace "Got variable entry:" entry)
  (log/trace "Got bounding-box:" bounding-box)
  (if (:umm entry)
    (let [original-dims (extract-dimensions entry)
          dims (normalize-lat-lon original-dims)
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
         :original-dimensions original-dims
         :dimensions dims
         :bounds bounding-box
         :opendap (create-opendap-bounds
                   dims bounding-box {:reversed? (lat-reversed? coll)})
         :size (get-in entry [:umm :Characteristics :Size])}))
    {:errors [errors/variable-metadata]}))
