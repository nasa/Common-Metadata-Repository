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
