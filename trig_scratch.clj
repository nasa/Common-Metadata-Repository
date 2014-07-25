
(do
  (require '[cmr.spatial.arc :as a])
  (require '[cmr.spatial.segment :as s])
  (require '[cmr.spatial.derived :as d])
  (use 'cmr.spatial.math))



; lat=atan((sin(lat1)*cos(lat2)*sin(lon-lon2)-sin(lat2)*cos(lat1)*sin(lon-lon1))/(cos(lat1)*cos(lat2)*sin(lon1-lon2)))
; lat=atan((A*sin(lon-lon2)-B*sin(lon-lon1))/bottom)

(def a (a/ords->arc -0.39,3.5, 10,10))

(let [{p1 :west-point p2 :east-point} a
      {lat1 :lat-rad lon1 :lon-rad} p1
      {lat2 :lat-rad lon2 :lon-rad} p2
      A (* (sin lat1) (cos lat2))
      B (* (sin lat2) (cos lat1))
      bottom (* (cos lat1) (cos lat2) (sin (- lon1 lon2)))
      equation (format "atan((%f * sin(x * π/180 - %f) - %f * sin(x * π/180 - %f)) / %f) * 180/π"
                       A lon2 B lon1 bottom)]

  (println equation)
  {:A A
   :B B
   :bottom bottom
   :lon2 lon2
   :lon1 lon1
   :equation equation})


(def ls (d/calculate-derived (s/ords->line-segment -10 5 -3 1)))

(let [{:keys [m b]} ls]
  (println (format "%f * x + %f" m b)))


atan((0.060121 * sin(x * π/180 - 0.174533) - 0.173324 * sin(x * π/180 - -0.006807)) / -0.177276) * 180/π

atan((0.060121 * sin(x * π/180 - 0.174533) - 0.173324 * sin(x * π/180 + 0.006807)) / -0.177276) * 180/π