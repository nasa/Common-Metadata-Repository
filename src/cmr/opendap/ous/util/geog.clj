(ns cmr.opendap.ous.util.geog
  (:require
   [cmr.opendap.const :as const]
   [taoensso.timbre :as log]))

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
  pre-decremented for use in OPeNDAP, sometimes not. This function attempts to
  ensure a consistent use of decremented max values for indices."
  ([max default-max]
   (offset-index max default-max const/default-lat-lon-resolution))
  ([max default-max resolution]
   (if (< max (* default-max resolution))
     max
     (dec max))))

;; XXX Can we use these instead? Why was the phase shifting written
;;     so obtrusely? There's got to be a reason, I just don't know it ...
;; XXX This is being tracked in CMR-4959
(defn new-lon-phase-shift
  [lon-max in]
  (int (Math/floor (+ (/ lon-max 2) in))))

(defn new-lat-phase-shift
  [lat-max in]
  (- lat-max
     (int (Math/floor (+ (/ lat-max 2) in)))))

;; The following longitudinal phase shift functions were translated from the
;; OUS Node.js prototype. It would be nice to use the more general functions
;; above, if those work out.

(defn lon-lo-phase-shift
  [lon-max lon-lo]
  (let [res (Math/ceil (/ lon-max const/default-lon-abs-hi))]
    (log/debug "Got lon-max:" lon-max)
    (log/debug "Got resolution:" res)
    (-> (/ (* (offset-index lon-max const/default-lon-abs-hi res)
              (adjusted-lon lon-lo res))
           (adjusted-lon const/default-lon-hi res))
        Math/floor
        int)))

(defn lon-hi-phase-shift
  [lon-max lon-hi]
  (let [res (Math/ceil (/ lon-max const/default-lon-abs-hi))]
    (log/debug "Got lon-max:" lon-max)
    (log/debug "Got resolution:" res)
    (-> (/ (* (offset-index lon-max const/default-lon-abs-hi res)
              (adjusted-lon lon-hi res))
           (adjusted-lon const/default-lon-hi res))
        Math/ceil
        int)))

(defn lat-lo-phase-shift
  "This is used for reading values from OPeNDAP where -90N is stored at the
  zero (first) index in the array."
  [lat-max lat-lo]
  (let [res (Math/ceil (/ lat-max const/default-lat-abs-hi))]
    (log/debug "Got lat-max:" lat-max)
    (log/debug "Got resolution:" res)
    (-> (/ (* (offset-index lat-max const/default-lat-abs-hi res)
              (adjusted-lat lat-lo res))
           (adjusted-lat const/default-lat-hi res))
        Math/floor
        int)))

(defn lat-hi-phase-shift
  "This is used for reading values from OPeNDAP where -90N is stored at the
  zero (first) index in the array."
  [lat-max lat-hi]
  (let [res (Math/ceil (/ lat-max const/default-lat-abs-hi))]
    (log/debug "Got lat-max:" lat-max)
    (log/debug "Got resolution:" res)
    (-> (/ (* (offset-index lat-max const/default-lat-abs-hi res)
              (adjusted-lat lat-hi res))
           (adjusted-lat const/default-lat-hi res))
        Math/ceil
        int)))

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
