(ns cmr.spatial.math
  (:require [primitive-math])
  (:import net.jafama.StrictFastMath))

(primitive-math/use-primitive-operators)

;; Generate function wrappers around java math methods that take a single arg.
(doseq [f '[cos sin tan atan abs sqrt acos asin]]
  (let [math-sym (symbol (str "StrictFastMath/" f))]
    (eval `(defn ~f ^double [^double v#]
             (~math-sym v#)))))

(defn atan2 ^double [^double y ^double x]
  (StrictFastMath/atan2 y x))

(def ^:const ^double PI Math/PI)

(def ^:const ^double TAU (* 2.0 PI))

(def ^:const ^double EARTH_RADIUS_METERS 6371000)

(defn radians ^double [^double d]
  (* d (/ PI 180.0)))

(defn degrees ^double [^double r]
  (* r (/ 180.0 PI)))

(defn sq ^double [^double v]
  (* v v))

(defn round
  "Rounds the value with the given precision"
  ^double [^long precision ^double v]
  (let [multiplier (Math/pow 10 precision)
        rounded (Math/round (* v multiplier))]
    (/ (double rounded) (double multiplier))))

(defn within-range? [^double v ^double min ^double max]
  (and (>= v min)
       (<= v max)))

(defn avg ^double [nums]
  (/ (double (apply clojure.core/+ nums)) (double (count nums))))

(defn mid
  "Returns the midpoint between two values"
  ^double [^double v1 ^double v2]
  (/ (+ v1 v2) 2.0))

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

(defn double-approx=
  "Determines if two double values are approximately equal. Created to avoid reflection."
  ([^double expected ^double n]
   (double-approx= expected n DELTA))
  ([^double expected ^double n ^double delta]
   (<= (abs (- n expected)) delta)))


(extend-protocol ApproximateEquivalency
  Number
  (approx=
    ([expected n]
     (double-approx= expected n))
    ([expected n delta]
     (double-approx= expected n delta)))

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
       (every? (fn [[e v]]
                 (approx= e v delta))
               (map vector expected actual))))))

