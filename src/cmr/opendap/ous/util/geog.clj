(ns cmr.opendap.ous.util.geog
  (:require
   [cmr.opendap.const :as const]
   [taoensso.timbre :as log]))

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
  `cmr.opendap.ous.variable/create-opendap-lookup-reversed`)."
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
  `cmr.opendap.ous.variable/create-opendap-lookup-reversed`)."
  [lat-max lat-lo]
  (let [res (Math/ceil (/ lat-max const/default-lat-abs-hi))]
    (int
      (- (offset-index lat-max const/default-lat-abs-hi res)
         (lat-hi-phase-shift lat-max lat-lo)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn bounding-box
  ([bounding-box reversed?]
    (bounding-box const/default-lon-abs-hi
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
         (create-opendap-lookup-reversed lon-lo lat-lo lon-hi lat-hi))
       (let [lat-lo (lat-lo-phase-shift lat-max lat-lo)
             lat-hi (lat-hi-phase-shift lat-max lat-hi)]
         (create-opendap-lookup lon-lo lat-lo lon-hi lat-hi))))))
