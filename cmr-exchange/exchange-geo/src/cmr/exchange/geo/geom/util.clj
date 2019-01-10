(ns cmr.exchange.geo.geom.util
  "Equations taken from the following:
  * http://earth-info.nga.mil/GandG/publications/tr8350.2/wgs84fin.pdf")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def earth-semi-major-axis 6378137.0)
(def earth-semi-minor-axis 6356752.3142)
(def earth-semi-major-axis**2 (Math/pow earth-semi-major-axis 2))
(def earth-semi-minor-axis**2 (Math/pow earth-semi-minor-axis 2))
(def earth-radius earth-semi-major-axis)
(def earth-radius**2 earth-semi-major-axis**2)
(def earth-area (* 4 Math/PI earth-radius**2))
(def earth-eccentricity 0.081819190842622)
(def earth-eccentricity**2 (Math/pow earth-eccentricity 2))
(def earth-linear-eccentricity 521854.00842339)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Supporting Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn prime-vertical-curvature-radius
  [lat-radians]
  (/ earth-semi-major-axis
     (Math/sqrt (- 1
                   (* earth-eccentricity**2
                      (Math/pow (Math/sin lat-radians) 2))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Conversion Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn lla->ecef
  "Taken from:
   * https://www.mathworks.com/matlabcentral/fileexchange/7942-covert-lat--lon--alt-to-ecef-cartesian

   Inputs are in degrees and meters; outputs are in meters."
  [lat lon alt]
  (let [lat-radians (Math/toRadians lat)
        lon-radians (Math/toRadians lon)
        n (prime-vertical-curvature-radius lat-radians)
        n-alt (+ n alt)
        x (* n-alt
            (Math/cos lat-radians)
            (Math/cos lon-radians))
        y (* n-alt
             (Math/cos lat-radians)
             (Math/sin lon-radians))
        z (* (+ (* n
                   (- 1 earth-eccentricity**2))
                alt)
             (Math/sin lat-radians))]
    [x y z]))

(defn ecef->lla
  "Taken from:
   * https://www.mathworks.com/matlabcentral/fileexchange/7941-convert-cartesian--ecef--coordinates-to-lat--lon--alt

   Currently no correction for instability in altitude near exact poles.

   Inputs are in meters; outputs are degrees and meters."
  [x y z]
  (let [ep (Math/sqrt (/ (- earth-semi-major-axis**2
                            earth-semi-minor-axis**2)
                         earth-semi-minor-axis**2))
        p (Math/hypot x y)
        theta (Math/atan2 (* earth-semi-major-axis z)
                       (* earth-semi-minor-axis p))
        lon (Math/atan2 y x)
        lat (Math/atan2 (+ z (* (Math/pow ep 2)
                                earth-semi-minor-axis
                                (Math/pow
                                 (Math/sin theta) 3)))
                        (- p (* earth-eccentricity**2
                                earth-semi-major-axis
                                (Math/pow
                                 (Math/cos theta) 3))))
        n (prime-vertical-curvature-radius lat)
        alt (- (/ p (Math/cos lat)) n)]
    [(Math/toDegrees lat)
     (Math/toDegrees (mod lon (* 2 Math/PI)))
     alt]))

(defn ll->cartesian
  "Inputs are in degrees; outputs are in meters."
  [lat lon]
  [(/ (* Math/PI earth-radius lon) 180)
   (* earth-radius (Math/sin (Math/toRadians lat)))])

(defn bbox->polypoints
  "Assumes CCW ordering of points."
  [bbox]
  )
