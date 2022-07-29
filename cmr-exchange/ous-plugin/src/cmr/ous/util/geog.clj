(ns cmr.ous.util.geog
  (:require
   [clojure.string :as string]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [cmr.ous.const :as const]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;   Notes   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;; Notes on representing spatial extents.
;;;;
;;;; EDSC uses URL-encoded long/lat numbers representing a bounding box
;;;; Note that the ordering is the same as that used by CMR (see below).
;;;;  `-9.984375%2C56.109375%2C19.828125%2C67.640625`
;;;; which URL-decodes to:
;;;;  `-9.984375,56.109375,19.828125,67.640625`
;;;;
;;;; OPeNDAP download URLs have something I haven't figured out yet; given that
;;;; one of the numbers if over 180, it can't be degrees ... it might be what
;;;; WCS uses for `x` and `y`?
;;;;  `Latitude[22:34],Longitude[169:200]`
;;;;
;;;; The OUS Prototype uses the WCS standard for lat/long:
;;;;  `SUBSET=axis[,crs](low,high)`
;;;; For lat/long this takes the following form:
;;;;  `subset=lat(56.109375,67.640625)&subset=lon(-9.984375,19.828125)`
;;;;
;;;; CMR supports bounding spatial extents by describing a rectangle using four
;;;; comma-separated values:
;;;;  1. lower left longitude
;;;;  2. lower left latitude
;;;;  3. upper right longitude
;;;;  4. upper right latitude
;;;; For example:
;;;;  `bounding_box==-9.984375,56.109375,19.828125,67.640625`
;;;;
;;;; Google's APIs use lower left, upper right, but the specify lat first, then
;;;; long:
;;;;  `southWest = LatLng(56.109375,-9.984375);`
;;;;  `northEast = LatLng(67.640625,19.828125);`

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants/Default Values   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defrecord ArrayLookup [low high lat-reversed?])

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
   ;; :umm :Characteristics :Size -- no longer used for UMM-Vars 1.2+
   size
   ;; A Boolean value indicating wheter latitude is recorded normally in
   ;; OPeNDAP (increasing indices starting at 0 correlating up from -90N to
   ;; 90N) or reversed (increasing indices starting at 0 correlating down from
   ;; 90N to -90N)
   lat-reversed?])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn lat-reversed?
  [lo-lat hi-lat]
  (> lo-lat hi-lat))

(defn parse-coord-part
  [value default-value]
  (cond (string? value) (Float/parseFloat value)
        (nil? value) default-value
        :else value))

(defn parse-lon-low
  [value]
  (parse-coord-part value const/default-lon-lo))

(defn parse-lon-high
  [value]
  (parse-coord-part value const/default-lon-hi))

(defn parse-lat-low
  [value]
  (parse-coord-part value const/default-lat-lo))

(defn parse-lat-high
  [value]
  (parse-coord-part value const/default-lat-hi))

(defn adjusted-lon
  ([lon]
   (adjusted-lon lon const/default-lat-lon-resolution))
  ([lon resolution]
   (- (* lon resolution)
      (* const/default-lon-lo resolution))))

(defn adjusted-lat
  ([lat]
   (adjusted-lat lat const/default-lat-lon-resolution))
  ([lat resolution]
   (- (* lat resolution)
      (* const/default-lat-lo resolution))))

(defn offset-index
  "OPeNDAP indices are 0-based, thus gridded longitudinal data with 1x
  resolution is stored at indices from 0 to 359 and similar latitudinal data is
  stored at indices from 0 to 179. The max values for lat and lon are stored in
  the UMM-Var records as part of the dimensions. Sometimes those values are
  pre-decremented for use in OPeNDAP, sometimes not (e.g., sometimes max
  longitude is given as 359, sometimes as 360). This function attempts to
  ensure a consistent use of decremented max values for indices."
  ([max default-max]
   (offset-index max default-max const/default-lat-lon-resolution))
  ([max default-max resolution]
   (if (< max (* default-max resolution))
     max
     (dec max))))

(defn phase-shift
  "Longitude goes from -180 to 180 and latitude from -90 to 90. However, when
  referencing data in OPeNDAP arrays, 0-based indices are needed. Thus in order
  to get indices that match up with degrees, our longitude needs to be
  phase-shifted by 180 degrees, latitude by 90 degrees."
  [degrees-max default-abs-degrees-max default-degrees-max degrees adjust-fn round-fn]
  (let [res (Math/ceil (/ degrees-max default-abs-degrees-max))]
    (log/trace "Got degrees-max:" degrees-max)
    (log/trace "Got degrees:" degrees)
    (log/trace "Got resolution:" res)
    (-> (/ (* (offset-index degrees-max default-abs-degrees-max res)
              (adjust-fn degrees res))
           (adjust-fn default-degrees-max res))
        round-fn
        int)))

(defn lon-lo-phase-shift
  [lon-max lon-lo]
  (phase-shift
   lon-max
   const/default-lon-abs-hi
   const/default-lon-hi
   lon-lo
   adjusted-lon
   #(Math/floor %)))

(defn lon-hi-phase-shift
  [lon-max lon-hi]
  (phase-shift
   lon-max
   const/default-lon-abs-hi
   const/default-lon-hi
   lon-hi
   adjusted-lon
   #(Math/ceil %)))

(defn lat-lo-phase-shift
  "This is used for reading values from OPeNDAP where -90N is stored at the
  zero (first) index in the array."
  [lat-max lat-lo]
  (phase-shift
   lat-max
   const/default-lat-abs-hi
   const/default-lat-hi
   lat-lo
   adjusted-lat
   #(Math/floor %)))

(defn lat-hi-phase-shift
  "This is used for reading values from OPeNDAP where -90N is stored at the
  zero (first) index in the array."
  [lat-max lat-hi]
  (phase-shift
   lat-max
   const/default-lat-abs-hi
   const/default-lat-hi
   lat-hi
   adjusted-lat
   #(Math/ceil %)))

(defn lat-lo-phase-shift-reversed
  "This is used for reading values from OPeNDAP where 90N is stored at the
  zero (first) index in the array.

  Note that this must also be used in conjunction with the hi and lo values
  for latitude in the OPeNDAP lookup array being swapped (see
  `cmr.metadata.proxy.concepts.variable/create-opendap-lookup-reversed`)."
  [lat-max lat-lo]
  (let [res (Math/ceil (/ lat-max const/default-lat-abs-hi))]
    (int
      (- (offset-index lat-max const/default-lat-abs-hi res)
         (lat-lo-phase-shift lat-max lat-lo)))))

(defn lat-hi-phase-shift-reversed
  "This is used for reading values from OPeNDAP where 90N is stored at the
  zero (first) index in the array.

  Note that this must also be used in conjunction with the hi and lo values
  for latitude in the OPeNDAP lookup array being swapped (see
  `cmr.metadata.proxy.concepts.variable/create-opendap-lookup-reversed`)."
  [lat-max lat-lo]
  (let [res (Math/ceil (/ lat-max const/default-lat-abs-hi))]
    (int
      (- (offset-index lat-max const/default-lat-abs-hi res)
         (lat-hi-phase-shift lat-max lat-lo)))))

(defn format-opendap-dim
  [min stride max]
  (if (or (nil? min) (nil? max))
    ""
    (format "[%s:%s:%s]" min stride max)))

(defn format-opendap-dim-lat
  ([lookup-record]
   (format-opendap-dim-lat lookup-record default-lat-lon-stride))
  ([lookup-record stride]
   (format-opendap-dim (get-in lookup-record [:low :lat])
                       stride
                       (get-in lookup-record [:high :lat]))))

(defn format-opendap-dim-lon
  ([lookup-record]
   (format-opendap-dim-lon lookup-record default-lat-lon-stride))
  ([lookup-record stride]
   (format-opendap-dim (get-in lookup-record [:low :lon])
                       stride
                       (get-in lookup-record [:high :lon]))))

(defn get-delimiter
  "Returns the delimiter of ; for DAP4 if is-dap4? parameter is true; otherwise returns , for DAP2."
  [is-dap4?]
  (if is-dap4?
    ";"
    ","))

(defn- normalized-name
  "Returns the normalized name. For DAP4 format, the name must start with /."
  [is-dap4? field]
  (if is-dap4?
    (if (string/starts-with? field "/")
      field
      (str "/" field))
    field))

(defn -format-opendap-lat-lon
  ([lookup-record [lon-name lat-name] stride]
   (-format-opendap-lat-lon lookup-record [lon-name lat-name] stride false))
  ([lookup-record [lon-name lat-name] stride is-dap4?]
    (format "%s%s%s%s%s"
            (normalized-name is-dap4? lat-name)
            (format-opendap-dim-lat lookup-record stride)
            (get-delimiter is-dap4?)
            (normalized-name is-dap4? lon-name)
            (format-opendap-dim-lon lookup-record stride))))

(def lat-type :LATITUDE_DIMENSION)
(def lon-type :LONGITUDE_DIMENSION)
(def lat-type? #(= % lat-type))
(def lon-type? #(= % lon-type))
(def lat-lon-type? #(or (lat-type? %) (lon-type? %)))

(defn lat-dim
  [dim]
  (or ;; This first one is all that will be needed by UMM-Var 1.2+
      (lat-type dim)
      ;; The remaining provide backwards compatibility with older Vars
      (:Latitude dim)
      (:latitude dim)
      (:lat dim)
      (:YDim dim)))

(defn lon-dim
  [dim]
  (or ;; This first one is all that will be needed by UMM-Var 1.2+
      (lon-type dim)
      ;; The remaining provide backwards compatibility with older Vars
      (:Longitude dim)
      (:longitude dim)
      (:lon dim)
      (:XDim dim)))

(defn restructure-dim
  [dim]
  (let [type (keyword (:Type dim))
        name (keyword (:Name dim))]
    [(if (lat-lon-type? type)
         type
         name) {:Size (:Size dim)
                :Name name
                :Type type}]))

(defn restructure-dims
  [dims]
  (->> dims
       (map restructure-dim)
       (into {})))

(defn normalize-lat-lon
  "This function normalizes the names of lat/lon in order to simplify internal,
  CMR-only logic. The original dimension names are recorded, and when needed,
  referenced."
  [dim]
  (-> dim
      (assoc :Latitude (lat-dim dim)
             :Longitude (lon-dim dim))
      (dissoc lat-type lon-type)
      ;; The dissoc here is only applicable to pre-1.2 UMM-Vars
      (dissoc :latitude :longitude :lat :lon :YDim :XDim)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-array-lookup
  "This is the convenience constructor for the ArrayLookup record, taking
  latitude and longitude values and outputing a data structure that can be
  used for creating the lookup indices for OPeNDAP dimensional arrays. It has
  can create output for both normal and reversed latitudinal arrays:

  * Pass the `reversed?` parameter with a value of `false` when latitude -90N
    is stored at the 0th index and 90N is stored at the highest index (whose
    actual number will varry, depending upon the resolution of the data). This
    is the default, when no value is passed for the `reversed?` parameter.

  * Pass the `reversed?` parameter with a value of `true` when latitude 90N is
    stored at the 0th index and -90N is stored at the highest index (whose
    actual number will varry, depending upon the resolution of the data)."
  ([lon-lo lat-lo lon-hi lat-hi]
    (create-array-lookup lon-lo lat-lo lon-hi lat-hi false))
  ([lon-lo lat-lo lon-hi lat-hi reversed?]
    (let [lookup (map->ArrayLookup
                  {:low {:lon lon-lo
                         :lat lat-lo}
                   :high {:lon lon-hi
                          :lat lat-hi}
                   :lat-reversed? reversed?})
          reversed-hi-lat (get-in lookup [:high :lat])
          reversed-lo-lat (get-in lookup [:low :lat])]
      (if reversed?
        (-> lookup
            (assoc-in [:low :lat] reversed-hi-lat)
            (assoc-in [:high :lat] reversed-lo-lat))
        lookup))))

(defn bounding-box->lookup-record
  ([bounding-box reversed?]
    (bounding-box->lookup-record const/default-lon-abs-hi
                                 const/default-lat-abs-hi
                                 bounding-box
                                 reversed?))
  ([lon-max lat-max
   [lon-lo lat-lo lon-hi lat-hi :as bounding-box]
   reversed?]
   (let [lon-lo (lon-lo-phase-shift lon-max lon-lo)
         lon-hi (lon-hi-phase-shift lon-max lon-hi)]
     (if reversed?
       (let [lat-lo (lat-lo-phase-shift-reversed lat-max lat-lo)
             lat-hi (lat-hi-phase-shift-reversed lat-max lat-hi)]
         (log/debug "Variable latitudinal values are reversed ...")
         (create-array-lookup lon-lo lat-lo lon-hi lat-hi reversed?))
       (let [lat-lo (lat-lo-phase-shift lat-max lat-lo)
             lat-hi (lat-hi-phase-shift lat-max lat-hi)]
         (create-array-lookup lon-lo lat-lo lon-hi lat-hi))))))

(defn bounding-box->lookup-indices
  ([bounding-box]
    (bounding-box->lookup-indices bounding-box false))
  ([bounding-box reversed?]
    (bounding-box->lookup-indices bounding-box
                                  reversed?
                                  ["Longitude" "Latitude"]))
  ([bounding-box reversed? index-names]
    (bounding-box->lookup-indices bounding-box
                                  reversed?
                                  index-names
                                  default-lat-lon-stride))
  ([bounding-box reversed? index-names stride]
    (bounding-box->lookup-indices const/default-lon-abs-hi
                                  const/default-lat-abs-hi
                                  bounding-box
                                  reversed?
                                  index-names
                                  stride))
  ([lon-max lat-max bounding-box reversed? index-names stride]
    (-format-opendap-lat-lon
      (bounding-box->lookup-record bounding-box reversed?)
      index-names
      stride)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Metadata   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn extract-indexranges
  [entry]
  (when entry
    (let [ranges (get-in entry [:umm :IndexRanges])
          lo-lon (parse-lon-low (first (:LonRange ranges)))
          hi-lon (parse-lon-high (last (:LonRange ranges)))
          lo-lat (parse-lat-low (first (:LatRange ranges)))
          hi-lat (parse-lat-high (last (:LatRange ranges)))
          reversed? (lat-reversed? lo-lat hi-lat)]
      (create-array-lookup lo-lon lo-lat hi-lon hi-lat reversed?))))

(defn create-opendap-bounds
  ([bounding-box]
   (create-opendap-bounds bounding-box {:reversed? false}))
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
     (bounding-box->lookup-record
      (:Size lon-max)
      (:Size lat-max)
      bounding-box
      (:reversed? opts)))))

(defn replace-defaults-lat-lon
  [bounding-info stride [k v]]
  (let [v (or (:Size v) v)]
    (cond (= k :Longitude) (format-opendap-dim-lon
                            (:opendap bounding-info) stride)
          (= k :Latitude) (format-opendap-dim-lat
                           (:opendap bounding-info) stride)
          :else (format-opendap-dim 0 stride (dec v)))))

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

(defn get-lat-lon-names
  [bounding-info]
  [(name (get-in bounding-info [:dimensions :Longitude :Name]))
   (name (get-in bounding-info [:dimensions :Latitude :Name]))])

(defn format-opendap-lat-lon
  ([bounding-info]
   (format-opendap-lat-lon bounding-info default-lat-lon-stride))
  ([bounding-info stride]
   (format-opendap-lat-lon bounding-info stride false))
  ([bounding-info stride is-dap4?]
   (-format-opendap-lat-lon (:opendap bounding-info)
                            (get-lat-lon-names bounding-info)
                            stride
                            is-dap4?)))

(defn format-opendap-bounds
  ([bounding-info]
   (format-opendap-bounds bounding-info default-lat-lon-stride))
  ([{bound-name :name :as bounding-info} stride]
   (format-opendap-bounds bounding-info stride false))
  ([{bound-name :name :as bounding-info} stride is-dap4?]
   (log/trace "Bounding info:" bounding-info)
   (format "%s%s"
           (normalized-name is-dap4? bound-name)
           (format-opendap-dims bounding-info stride))))

(defn extract-dimensions
  [entry]
  (restructure-dims
   (get-in entry [:umm :Dimensions])))

(defn extract-bounding-info
  "This function is executed at the variable level, however it has general,
  non-variable-specific bounding info passed to it in order to support
  spatial subsetting"
  [entry bounding-box]
  (log/trace "Got variable entry:" entry)
  (log/trace "Got bounding-box:" bounding-box)
  (if (:umm entry)
    (let [dims (normalize-lat-lon (extract-dimensions entry))
          var-array-lookup (extract-indexranges entry)
          reversed? (:lat-reversed? var-array-lookup)]
      (map->BoundingInfo
        {:concept-id (get-in entry [:meta :concept-id])
         :name (get-in entry [:umm :Name])
         :dimensions dims
         :bounds bounding-box
         :opendap (create-opendap-bounds
                   dims
                   bounding-box
                   {:reversed? reversed?})
         :size (get-in entry [:umm :Characteristics :Size])
         :lat-reversed? reversed?}))
    {:errors [metadata-errors/variable-metadata]}))
