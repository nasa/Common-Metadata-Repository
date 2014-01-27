(ns cmr.common.tests.simple-check-ext
  (:require [simple-check.generators :as gen])
  (:import java.util.Random))

(defn model-gen
  "Creates a generator for models. Takes the constructor function for the model and generators
  for each argument."
  [constructor & args]
  (gen/fmap (partial apply constructor) (apply gen/tuple args)))

(defn counter
  "Creates a generator that returns numbers in sequence from 0 to infinity."
  []
  (let [a (atom -1)]
    (gen/make-gen (fn [rnd size]
                    (swap! a inc)))))

(defn return-then
  "Creates a generator that returns each of the values in constants then uses then-gen
  for subsequent values. This is useful when there are certain values that you want
  always generated during testing.

  user=>(gen/sample (return-then [:a :b :c] (gen/symbol)))
  (\"a\" \"b\" \"c\" \"N\" \"_nN\" \"dVH3\" \"t@\" \"k+\" \"?Oq->h\" \"X82-<6bVp\")"
  [constants then-gen]
  (gen/gen-bind (counter)
                (fn [i]
                  (if (< i (count constants))
                    (gen/return (nth constants i))
                    then-gen))))

(defn- double-halfs
  [n]
  (take-while #(> (Math/abs (double %)) 0.0001) (iterate #(/ % 2.0) n)))

(defn- shrink-double
  [dbl]
  (map (partial - dbl) (double-halfs dbl)))

(defn- double-rose-tree
  [value]
  [value (map double-rose-tree (shrink-double value))])

(defn- rand-double-range
  [^Random rnd lower upper]
  (let [diff (Math/abs (double (- upper lower)))]
    (if (zero? diff)
      lower
      (+ (* diff (.nextDouble rnd)) lower))))

(defn choose-double
  "Creates a generator that returns double values between lower and upper inclusive."
  [lower upper]
  (gen/make-gen
    (fn [^Random rnd _size]
      (let [value (rand-double-range rnd lower upper)]
        (gen/rose-filter
          #(and (>= % lower) (<= % upper))
          [value (map double-rose-tree (shrink-double value))])))))

