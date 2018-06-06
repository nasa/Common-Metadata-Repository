(ns cmr.opendap.ous.util.geog
  (:require
   [cmr.opendap.const :as const]
   [taoensso.timbre :as log]))

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

;; The following longitudinal phase shift functions are translated from the
;; OUS Node.js prototype. It would be nice to use the more general functions
;; above, if those work out.

(defn lon-lo-phase-shift
  [lon-max lon-lo]
  (log/debug "Got lon-max:" lon-max)
  (-> (/ (* (dec lon-max)
            (- lon-lo const/default-lon-lo))
         (- const/default-lon-hi const/default-lon-lo))
      Math/floor
      int))

(defn lon-hi-phase-shift
  [lon-max lon-hi]
  (log/debug "Got lon-max:" lon-max)
  (-> (/ (* (dec lon-max)
            (- lon-hi const/default-lon-lo))
         (- const/default-lon-hi const/default-lon-lo))
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
  (-> (/ (* (dec lat-max)
            (- lat-lo const/default-lat-lo))
          (- const/default-lat-hi const/default-lat-lo))
       Math/floor
       int))

(defn orig-lat-hi-phase-shift
  [lat-max lat-hi]
  (-> (/ (* (dec lat-max)
            (- lat-hi const/default-lat-lo))
          (- const/default-lat-hi const/default-lat-lo))
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
       (Math/floor (/ (* (dec lat-max)
                         (- lat-lo const/default-lat-lo))
                      (- const/default-lat-hi const/default-lat-lo))))))

(defn lat-hi-phase-shift
  [lat-max lat-hi]
  (log/debug "Got lat-max:" lat-max)
  (int
    (- lat-max
       1
       (Math/ceil (/ (* (dec lat-max)
                        (- lat-hi const/default-lat-lo))
                     (- const/default-lat-hi const/default-lat-lo))))))
