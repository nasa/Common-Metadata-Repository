




(def p1 {:x -45.0
         :y 0.0})
(def p2 {:x 0.0
         :y 30.0})

(def p3 {:x -45.0
         :y 30.0})
(def p4 {:x 0.0
         :y 0.0})

;; Intersection should be at -23, 14
;; Arc intersection is at -20.398,15.887

(def line1 (points->line p1 p2))
(def line2 (points->line p3 p4))

(def intersection-point (line-intersection-point line1 line2))
(comment {:x -22.12299465240642, :y 14.748663101604278})


;; midway point
(line+x->y line1 -22.5)
(line+x->y line2 -22.5)

(line+x->y line1 -30)

(line+y->x line1 16.175960878620344)
(line+x->y line1 -20.772845524037166)

(+ (/ (- 17.351921757240685 15.0) 2.0) 15.0)
(+ (/ (- -20.80963236600485 -20.736058682069483) 2.0) -20.736058682069483)

(defn points->line [p1 p2]
  "Calculates the the slope (m) and intercept (b) of a line. The formula for a line is y = m * x + b"
  (let [{^double x1 :x ^double y1 :y} p1
        {^double x2 :x ^double y2 :y} p2
        m (/ (- y2 y1) (- x2 x1))
        b (- y1 (* m x1))]
    {:m m
     :b b}))

(defn line+x->y
  "Returns the y coordinate on the line at the given x coordinate."
  [line ^double x]
  (let [{:keys [^double m ^double b]} line]
    (+ (* m x) b)))

(defn line+y->x
  "Returns the x coordinate on the line at the given y coordinate."
  [line ^double y]
  (let [{:keys [^double m ^double b]} line]
    (/ (- y b) m)))

(defn line-intersection-point
  "Finds the intersection point of a line by setting the y's equal to each other. m1*x+b1= m2*x+b2"
  [line1 line2]
  (let [{^double m1 :m ^double b1 :b} line1
        {^double m2 :m ^double b2 :b} line2
        x (/ (- b2 b1) (- m1 m2))]
    {:x x
     :y (line+x->y line1 x)}))


