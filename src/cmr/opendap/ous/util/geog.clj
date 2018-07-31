(ns cmr.opendap.ous.util.geog
  (:require
   [cmr.opendap.const :as const]
   [taoensso.timbre :as log]))

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
   ;; :umm :Characteristics :Size -- no longer used for UMM-Vars 1.2+
   size])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  `cmr.opendap.ous.concepts.variable/create-opendap-lookup-reversed`)."
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
  `cmr.opendap.ous.concepts.variable/create-opendap-lookup-reversed`)."
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

(defn format-opendap-lat-lon
  ([lookup-record [lon-name lat-name] stride]
    (format "%s%s,%s%s"
            lat-name
            (format-opendap-dim-lat lookup-record stride)
            lon-name
            (format-opendap-dim-lon lookup-record stride))))

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
                          :lat lat-hi}})
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
    (bounding-box->lookup-indices bounding-box ["Longitude" "Latitude"]))
  ([bounding-box index-names]
    (bounding-box->lookup-indices bounding-box false index-names))
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
    (format-opendap-lat-lon
      (bounding-box->lookup-record bounding-box reversed?)
      index-names
      stride)))

