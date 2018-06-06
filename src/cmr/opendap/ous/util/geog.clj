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
  (log/debug "Got lon-max:" lon-max)
  (-> (/ (* (offset-index lon-max const/default-lon-abs-hi)
            (adjusted-lon lon-lo))
         (adjusted-lon const/default-lon-hi))
      Math/floor
      int))

(defn lon-hi-phase-shift
  [lon-max lon-hi]
  (log/debug "Got lon-max:" lon-max)
  (-> (/ (* (offset-index lon-max const/default-lon-abs-hi)
            (adjusted-lon lon-hi))
         (adjusted-lon const/default-lon-hi))
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
  [lat-max lat-lo]
  (-> (/ (* (offset-index lat-max const/default-lat-abs-hi)
            (adjusted-lat lat-lo))
          (adjusted-lat const/default-lat-hi))
       Math/floor
       int))

(defn orig-lat-hi-phase-shift
  [lat-max lat-hi]
  (-> (/ (* (offset-index lat-max const/default-lat-abs-hi)
            (adjusted-lat lat-hi))
          (adjusted-lat const/default-lat-hi))
       Math/ceil
       int))

;; The following latitudinal phase shift functions are what is currently being
;; used.

(defn lat-lo-phase-shift
  [lat-max lat-lo]
  (log/debug "Got lat-max:" lat-max)
  (int
    (- lat-max
       1
       (Math/floor (/ (* (offset-index lat-max const/default-lat-abs-hi)
                         (adjusted-lat lat-lo))
                      (adjusted-lat const/default-lat-hi))))))

(defn lat-hi-phase-shift
  [lat-max lat-hi]
  (log/debug "Got lat-max:" lat-max)
  (int
    (- lat-max
       1
       (Math/ceil (/ (* (offset-index lat-max const/default-lat-abs-hi)
                        (adjusted-lat lat-hi))
                     (adjusted-lat const/default-lat-hi))))))
