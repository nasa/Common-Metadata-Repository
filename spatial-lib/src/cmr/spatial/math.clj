(ns cmr.spatial.math
  [:require
   [cmr.common.util :as util]
   [primitive-math]]
  (:import
   (net.jafama StrictFastMath)))

(primitive-math/use-primitive-operators)

(defmacro cos [v]
  `(StrictFastMath/cos ~v))

(defmacro sin [v]
  `(StrictFastMath/sin ~v))

(defmacro tan [v]
  `(StrictFastMath/tan ~v))

(defmacro atan [v]
  `(StrictFastMath/atan ~v))

(defmacro sqrt [v]
  `(StrictFastMath/sqrt ~v))

(defmacro acos [v]
  `(StrictFastMath/acos ~v))

(defmacro asin [v]
  `(StrictFastMath/asin ~v))

(defmacro abs [v]
  `(Math/abs ~v))

(defmacro atan2 [y x]
  `(StrictFastMath/atan2 ~y ~x))

(def ^:const ^long LESS_THAN
  "Value to return from compare functions to indicate a value is less than another value"
  -1)

(def ^:const ^long GREATER_THAN
  "Value to return from compare functions to indicate a value is greater than another value"
  1)

(def ^:const ^double infinity
  (double Double/POSITIVE_INFINITY))

(def ^:const ^double negative-infinity
  (double Double/NEGATIVE_INFINITY))

(def ^:const ^double PI Math/PI)

(def ^:const ^double TAU (* 2.0 PI))

(def ^:const ^double EARTH_RADIUS_METERS 6367435.0)

(def ^:const ^double SOLAR_DAY_S (* 24.0 3600.0))

(def ^:const ^double EARTH_ANGULAR_VELOCITY_RAD_S (/ TAU SOLAR_DAY_S))

(defn even-long?
  "A copy of the clojure even? function but with long type annotations for primitive math performance."
  [^long l]
  (zero? (rem l 2)))

(defn odd-long?
  "A copy of the clojure odd? function but with long type annotations for primitive math performance."
  [l]
  (not (even-long? l)))

(defn radians
  "Converts degrees to radians"
  ^double [^double d]
  (* d (/ PI 180.0)))

(defn degrees
  "Converts radians to degrees"
  ^double [^double r]
  (* r (/ 180.0 PI)))

(defmacro sq
  "Returns the square of the given value."
  [v]
  `(* ~v ~v))

(defn round
  "Rounds the value with the given precision"
  ^double [^long precision ^double v]
  ;; There is a slight performance improvement to avoid the use of pow if possible. These are all
  ;; precision values that we use currently.
  (let [rounding-multiplier (case precision
                              4 10000.0
                              5 100000.0
                              7 10000000.0
                              8 100000000.0
                              11 100000000000.0
                              ;; else
                              (Math/pow 10 precision))]
    (if (< v 0.0)
      (* (round precision (abs v)) -1.0)
      ;; Implements rounding in a way that avoids _most_ double precision problems.
      ;; This will still fail for some values like 0.00499999999999 with 2 precision. It will round that up.
      ;; This tradeoff is made for performance.
      (/ (Math/floor (+ (* v rounding-multiplier) 0.500000000001)) rounding-multiplier))))

(defn float->double
  "Converts a float to a double in a way that will keep the double value closer to the original
  float value than a cast would do.
  (double (float 0.1)) => 0.10000000149011612
  (float->double (float 0.1)) => 0.1
  See http://programmingjungle.blogspot.com/2013/03/float-to-double-conversion-in-java.html"
  ^double [v]
  (.doubleValue (Double. (str v))))

(defn- shift-float
  "Shifts the mantissa of the float by 1 in the positive of negative direction to increase the float
  or decrease it to the very next possible value representable by a float."
  [v up?]
  (when (zero? ^float v)
    (throw (Exception. "Cannot shift float that is 0.")))
  (let [shiftfn (if (> ^float v 0.0)
                  (if up? clojure.core/inc clojure.core/dec)
                  (if up? clojure.core/dec clojure.core/inc))]
    (Float/intBitsToFloat (shiftfn (Float/floatToIntBits v)))))

(defn double->float
  "Converts a double to a float rounding either up or down as indicated"
  [^double d round-up?]
  (if (zero? d)
    (float 0.0)
    (let [f (float d)
          increased? (> (float->double f) d)]
      (cond
        (= round-up? increased?) f

        ;; It decreased and it should be rounded up
        round-up? (shift-float f true)

        ;; It increased and it should be rounded down
        :else (shift-float f false)))))


(defn float-type?
  "Returns true if value is a java Float"
  [v]
  (= Float (type v)))

(defmacro within-range?
  "Returns true if v is within min and max."
  [v min max]
  `(and (>= ~v ~min) (<= ~v ~max)))

(defmacro range-intersects?
  "Returns true if range2 intersects range 1"
  [r1min r1max r2min r2max]
  `(or (within-range? ~r2min ~r1min ~r1max)
       (within-range? ~r2max ~r1min ~r1max)
       (within-range? ~r1min ~r2min ~r2max)))

(defn avg
  "Computes the average of the numbers"
  ^double [nums]
  (/ (double (apply clojure.core/+ nums)) (double (count nums))))

(defn mid
  "Returns the midpoint between two values"
  ^double [^double v1 ^double v2]
  (/ (+ v1 v2) 2.0))

(defmacro constrain
  "Constrains the value to within the range.
  Written as a macro so it doesn't dictate the value types"
  [v min-v max-v]
  `(cond
     (> ~v ~max-v) ~max-v
     (< ~v ~min-v) ~min-v
     :else ~v))

(defn mid-lon
  "Returns the middle longitude between two lons. Order matters"
  ^double [^double w ^double e]
  (if (< w e)
    (+ w (/ (- e w) 2.0))
    (let [size (+ (- 180.0 w) (- e -180.0))
          mid (+ w (/ size 2.0))]
      (if (> mid 180.0)
        (- mid 360.0)
        mid))))

(defn antipodal-lon
  "Returns the longitude on the opposite side of the earth"
  [^double lon]
  (let [new-lon (+ 180.0 lon)]
    (if (> new-lon 180.0)
      (- new-lon 360.0)
      new-lon)))

(def ^:const ^double DELTA
  "The delta to use when considering if two values are approximately equal."
  0.00001)

(defprotocol ApproximateEquivalency
  "Determines if two objects are approximately equal. This is useful for finding expected values
  when doing floating point math that may result in rounding errors."
  (approx=
    [expected n]
    [expected n delta]
    "Returns true if n is within a small delta of expected."))

(defmacro double-approx=
  "Determines if two double values are approximately equal. Created to avoid reflection."
  ([expected n]
   `(double-approx= ~expected ~n ~DELTA))
  ([expected n delta]
   `(<= (abs (- ~n ~expected)) ~delta)))

(extend-protocol ApproximateEquivalency
  Number
  (approx=
    ([expected ^double n]
     (double-approx= (double expected) n))
    ([expected ^double n ^double delta]
     (double-approx= (double expected) n delta)))

  clojure.lang.IPersistentMap
  (approx=
    ([expected actual]
     (approx= expected actual DELTA))
    ([expected actual delta]
     (when (contains? (ancestors (class actual)) clojure.lang.IPersistentMap)
       (and
         (= (set (keys expected)) (set (keys actual)))
         (every?
           #(approx= (% expected) (% actual) delta)
           (keys expected))))))

  ;; Not supporting equivalency with sets due to the complexity.
  ; clojure.lang.IPersistentSet

  clojure.lang.Sequential
  (approx=
    ([expected actual]
     (approx= expected actual DELTA))
    ([expected actual delta]
     (when (contains? (ancestors (class actual)) clojure.lang.Sequential)
       (and (= (count expected) (count actual))
            (every? (fn [[e v]]
                      (approx= e v delta))
                    (map vector expected actual)))))))

(defn- angle-delta
  "Find the difference between a pair of angles."
  ^double [^double a1 ^double a2]
  (let [a2 (if (< a2 a1)
             ;; Shift angle 2 so it is always greater than angle 1. This allows
             ;; us to get the real radial distance between angle 2 and angle 1
             (+ 360.0 a2)
             a2)
        ;; Then when we subtract angle 1 from angle 2 we're asking "How far do
        ;; we have to turn to the  left to get to angle 2 from angle 1?"
        left-turn-amount (- a2 a1)]
    ;; Determine which is smaller: turning to the left or turning to the right
    (cond
      ;; In this case we can't determine whether turning to the left or the
      ;; right is smaller. We handle this by returning 0. Summing the angle
      ;; deltas in this case will == 180 or -180
      (== 180.0 left-turn-amount) 0
      ;; Turning to the right is less than turning to the left in this case.
      ;; Returns a negative number between 0 and -180.0
      (> left-turn-amount 180.0) (- left-turn-amount 360.0)
      :else left-turn-amount)))

(defn rotation-direction
  "A helper function that determines the final rotation direction based on a set of angles in
  degrees. It works by summing the differences between each angle. A net negative means clockwise,
  net positive is counter clockwise, and approximatly zero means that there was no net turn in either
  direction.
  Returns one of three keywords, :none, :counter-clockwise, or :clockwise, to indicate net direction
  of rotation"
  [angles]
  (let [angles (vec angles)
        num-angles (count angles)
        ^double net (loop [index 0
                           m 0.0]
                      (if (or (>= index num-angles) (> (+ index 2) num-angles))
                        m
                        (recur (inc index)
                               (+ m (angle-delta (nth angles index)
                                                 (nth angles (inc index)))))))]
    (cond
      (< (abs net) 0.01) :none
      (> net 0.0) :counter-clockwise
      :else :clockwise)))
