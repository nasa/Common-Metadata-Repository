(ns cmr.opendap.ous.concepts.variable
  (:require
   [clojure.string :as string]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.const :as const]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [cmr.opendap.ous.util.geog :as geog]
   [cmr.opendap.results.core :as results]
   [cmr.opendap.results.errors :as errors]
   [cmr.opendap.util :as util]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX We can pull this from configuration once async-get-metadata function
;;     signatures get updated to accept the system data structure as an arg.
(def variables-api-path "/variables")
(def pinned-variable-schema-version "1.2")
(def results-content-type "application/vnd.nasa.cmr.umm_results+json")
(def charset "charset=utf-8")
(def accept-format "%s; version=%s; %s")

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

;; XXX The following set and function are a hard-coded work-around for the
;;     fact that we don't currently have a mechanism for identifying the
;;     "direction of storage" or "endianness" of latitude data in different
;;     data sets: some store data from -90 to 90N starting at index 0, some
;;     from 90 to -90.
;;
;; XXX This is being tracked in CMR-4982
(def lat-reversed-datasets
  #{"Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"
    "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC [Testing Variable Dimensions]"
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
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalize-lat-lon
  "This function normalizes the names of lat/lon in order to simplify internal,
  CMR OPeNDAP-only logic. The original dimension names are recorded, and when
  needed, referenced."
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
    (let [url (str search-endpoint variables-api-path)
          payload (build-query variable-ids)]
      (log/debug "Variables query CMR URL:" url)
      (log/debug "Variables query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept (format accept-format
                                       results-content-type
                                       pinned-variable-schema-version
                                       charset))
           (request/add-form-ct)
           (request/add-payload payload)
           ((fn [x] (log/trace "Full request:" x) x)))
       response/json-handler))
    (deliver (promise) [])))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error errors/variable-metadata)
        (log/error rslts)
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
    bounding-box
    opts]
   (log/trace "Got dimensions:" dimensions)
   (when bounding-box
     (geog/bounding-box->lookup-record
      lon-max lat-max bounding-box (:reversed? opts)))))

(defn replace-defaults-lat-lon
  [bounding-info stride [k v]]
  (cond (= k :Longitude) (geog/format-opendap-dim-lon
                          (:opendap bounding-info) stride)
        (= k :Latitude) (geog/format-opendap-dim-lat
                         (:opendap bounding-info) stride)
        :else (geog/format-opendap-dim 0 stride (dec v))))

(defn format-opendap-dims
  ([bounding-info]
    (format-opendap-dims bounding-info geog/default-dim-stride))
  ([bounding-info stride]
    (if (:opendap bounding-info)
      (->> bounding-info
           :dimensions
           (map (partial replace-defaults-lat-lon bounding-info stride))
           (apply str))
      "")))

(defn get-lat-lon-names
  [bounding-info]
  (log/debug "Original dimensions:" (:original-dimensions bounding-info))
  [(cond (get-in bounding-info [:original-dimensions :Longitude])
         "Longitude"
         (get-in bounding-info [:original-dimensions :lon])
         "lon"
         ;; XXX See CMR-4985
         (get-in bounding-info [:original-dimensions :XDim])
         "XDim")
   (cond (get-in bounding-info [:original-dimensions :Latitude])
         "Latitude"
         (get-in bounding-info [:original-dimensions :lat])
         "lat"
         ;; XXX See CMR-4985
         (get-in bounding-info [:original-dimensions :YDim])
         "YDim")])

(defn format-opendap-lat-lon
  ([bounding-info]
   (format-opendap-lat-lon bounding-info geog/default-lat-lon-stride))
  ([bounding-info stride]
   (geog/format-opendap-lat-lon (:opendap bounding-info)
                                (get-lat-lon-names bounding-info)
                                stride)))

(defn format-opendap-bounds
  ([bounding-info]
   (format-opendap-bounds bounding-info geog/default-lat-lon-stride))
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
      (geog/map->BoundingInfo
        {:concept-id (get-in entry [:meta :concept-id])
         :name (get-in entry [:umm :Name])
         :original-dimensions original-dims
         :dimensions dims
         :bounds bounding-box
         :opendap (create-opendap-bounds
                   dims bounding-box {:reversed? (lat-reversed? coll)})
         :size (get-in entry [:umm :Characteristics :Size])}))
    {:errors [errors/variable-metadata]}))
