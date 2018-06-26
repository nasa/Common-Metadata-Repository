(ns cmr.opendap.geom.util)

(def earth-radius 6378137.0)

(defn latlon->WGS84
  [lat lon]
  [(/ (* Math/PI earth-radius lon) 180)
   (* earth-radius (Math/sin (Math/toRadians lat)))])
