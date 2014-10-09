(ns earth.driver
  (:require [vdd-core.core :as vdd]
            [vdd-core.internal.project-viz :as project-viz]))

(comment
  ;; want to send data over to be visualized. What could we visualize?
  ;; rings with a label
  ;; points with a label
  ;; bounding box

  (def ring {:type :ring
             :ords [-55.3,30 -55.3,27, -43,27, -43,30, -55.3,30]
             :ptions {:hidingPoints false}})

  {:type :point
   :ords []
   :label "30"}

  {:type :bounding-rectangle
   :west 0
   :north 0
   :east 0
   :south 0
   :label "30"}

  (defn point [lon lat label balloon]
    {:type :point
     :lon lon
     :lat lat
     :label label
     :balloon balloon})


  (set-viz-geometries [(point 1 1 "hi" "hello")])

  (set-viz-geometries [{:type :bounding-rectangle
                        :west 160
                        :north 10
                        :east 170
                        :south -10
                        :options {:draggable true}}])

  (set-viz-geometries [{:type :cartesian-polygon
                        :ords [-46 -1, 1 -1, 1 30, -46 -1 ]
                        :options {:draggable false}}
                       {:type :ring
                        :ords [-45 30, 0 0]}])

  ;; making the cartesian area mirror arc
  (set-viz-geometries [{:type :cartesian-polygon
                        :ords [-45 0, 1 -1, 0 30, -45 -0]
                        :options {:draggable false}}
                       ; {:type :ring
                       ;  :ords [-45 30, 0 0]}

                       {:type :ring
                        :ords [-45 0, 0 30]}

                       ; {:type :ring
                       ;  :ords [-30 10, -15 20]}

                       ])

  ;; line segment intersection point
  ; (add-viz-geometries [{:type :point
  ;                       :lon -22.12299465240642
  ;                       :lat 14.748663101604278}])

  ; ;; arc intersection point
  ; (add-viz-geometries [{:type :point
  ;                       :lon -22.225270322709946
  ;                       :lat 17.162733328149415}])
  (add-viz-geometries [{:type :point
                        :lon -20.945413480816008
                        :lat 16.271461041452543}])


  ;;; approximation method
  ;; arc midpoint
  (add-viz-geometries [{:type :point
                        :lon -22.5
                        :lat 17.351921757240685}])

  ;; line midpoint (equivalent to mid lon of mbr intersection)
  (add-viz-geometries [{:type :point
                        :lon -22.5
                        :lat 15.0}])

  ;; midway (lat) between midpoints
  (add-viz-geometries [{:type :point
                        :lon -22.5
                        :lat 16.175960878620344}])

  ;; arc intersection and midway lat
  (add-viz-geometries [{:type :point
                        :lon -20.80963236600485
                        :lat 16.175960878620344}])

  ;; line intersection at midway lat
  (add-viz-geometries [{:type :point
                        :lon -20.736058682069483
                        :lat 16.175960878620344}])

  ;; midway (lon) between intersections
  (add-viz-geometries [{:type :point
                        :lon -20.772845524037166
                        :lat 16.175960878620344}])

  ;; arc intersection and midway lon
  (add-viz-geometries [{:type :point
                        :lon -20.772845524037166
                        :lat 16.150056475830688}])

  (add-viz-geometries [{:type :point
                        :lon -20.772845524037166
                        :lat 16.15143631730856}])




  )


(defn set-viz-geometries [geometries]
  (vdd/data->viz {:cmd :set-geometries
                  :geometries geometries}))

(defn add-viz-geometries [geometries]
  (vdd/data->viz {:cmd :add-geometries
                  :geometries geometries}))

(defn remove-geometries [ids]
  (vdd/data->viz {:cmd :remove-geometries
                  :ids ids}))

(defn clear-viz-geometries []
  (vdd/data->viz {:cmd :clear-geometries}))


