(ns cmr.common.util
  "Utility functions that might be useful throughout the CMR."
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [camel-snake-kebab :as csk]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as w])
  (:import java.text.DecimalFormat))

(defn trunc
  "Returns the given string truncated to n characters."
  [s n]
  (when s
    (subs s 0 (min (count s) n))))

(defn sequence->fn
  [vals]
  "Creates a stateful function that returns individual values from the sequence. It returns the first
  value when called the first time, the second value on the second call and so on until the sequence
  is exhausted of values. Returns nil forever after that.

      user=> (def my-ints (sequence->fn [1 2 3]))
      user=> (my-ints)
      1
      user=> (my-ints)
      2
      user=> (my-ints)
      3
      user=> (my-ints)
      nil"
  (let [vals-atom (atom {:curr-val nil :next-vals (seq vals)})]
    (fn []
      (:curr-val (swap! vals-atom
                        (fn [{:keys [next-vals]}]
                          {:curr-val (first next-vals)
                           :next-vals (rest next-vals)}))))))

(defmacro future-with-logging
  "Creates a future that will log when a task starts and completes or if exceptions occur."
  [taskname & body ]
  `(future
    (info "Starting " ~taskname)
    (try
      (let [result# (do ~@body)]
        (info ~taskname " completed without exception")
        result#)
      (catch Throwable e#
        (error e# "Exception in " ~taskname)
        (throw e#))
      (finally
        (info ~taskname " complete.")))))

(defmacro time-execution
  "Times the execution of the body and returns a tuple of time it took and the results"
  [& body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)]
     [(- (System/currentTimeMillis) start#) result#]))


(defn build-validator
  "Creates a function that will call f with it's arguments. If f returns any errors then it will
  throw a service error of the type given."
  [error-type f]
  (fn [& args]
    (when-let [errors (apply f args)]
      (when (> (count errors) 0)
        (errors/throw-service-errors error-type errors)))))

(defn compose-validations
  "Creates a function that will compose together a list of validation functions into a
  single function that will perform all validations together"
  [validation-fns]
  (fn [& args]
    (reduce (fn [errors validation]
              (if-let [new-errors (apply validation args)]
                (concat errors new-errors)
                errors))
            []
            validation-fns)))

(defmacro record-fields
  "Returns the set of fields in a record type as keywords. The record type passed in must be a java
  class. Uses the getBasis function on record classes which returns a list of symbols of the fields of
  the record."
  [record-type]
  `(map keyword  ( ~(symbol (str record-type "/getBasis")))))

(defn remove-nil-keys
  "Removes keys mapping to nil values in a map.
  From http://stackoverflow.com/questions/3937661/remove-nil-values-from-a-map"
  [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn map-keys [f m]
  "Maps f over the keys in map m and updates all keys with the result of f.
  This is a recommended function from the Camel Snake Kebab library."
  (when m
    (letfn [(handle-value [v]
                          (cond
                            (map? v) (map-keys f v)
                            (vector? v) (mapv handle-value v)
                            (seq? v) (map handle-value v)
                            :else v))
            (mapper [[k v]]
                    [(f k) (handle-value v)])]
      (into {} (map mapper m)))))

(defn map-values
  "Maps f over all the values in m returning a new map with the updated values"
  [f m]
  (into {} (for [[k v] m] [k (f v)])))

(defn map-keys->snake_case
  "Converts map keys to snake_case."
  [m]
  (map-keys csk/->snake_case_keyword m))

(defn map-keys->kebab-case
  "Converts map keys to kebab-case"
  [m]
  (map-keys csk/->kebab-case-keyword m))

(defn map-n
  "Calls f with every step count elements from items. Equivalent to (map f (partition n step items))
  but faster. Note that it drops partitions at the end that would be less than a length of n."
  ([f n items]
   (map-n f n n items))
  ([f ^long n ^long step items]
   (let [items (vec items)
         size (count items)]
     (loop [index 0 results (transient [])]
       (let [subvec-end (+ index n)]
         (if (or (>= index size) (> subvec-end size))
           (persistent! results)
           (let [sub (subvec items index subvec-end)]
             (recur (+ index step) (conj! results (f sub))))))))))

(defn map-n-all
  "Calls f with every step count elements from items. Equivalent to (map f (partition-all n step items))
  but faster. Includes sets at the end that could be less than a lenght of n."
  ([f n items]
   (map-n-all f n n items))
  ([f ^long n ^long step items]
   (let [items (vec items)
         size (count items)]
     (loop [index 0 results (transient [])]
       (let [subvec-end (min (+ index n) size)]
         (if (>= index size)
           (persistent! results)
           (let [sub (subvec items index subvec-end)]
             (recur (+ index step) (conj! results (f sub))))))))))

(defn pmap-n-all
  "Splits work up n ways across futures and executes it in parallel. Calls the function with a set of
  n or fewer at the end. Not lazy - Items will be evaluated fully.

  Note that n is _not_ the number of threads that will be used. It's the number of items that will be
  processed in each parallel batch."
  [f n items]
  (let [build-future (fn [subset]
                       (future
                         (try
                           (f subset)
                           (catch Throwable t
                             (error t (.getMessage t))
                             (throw t)))))
        futures (map-n-all build-future n items)]
    (mapv deref futures)))

(defmacro while-let
  "A macro that's similar to when let. It will continually evaluate the bindings and execute the body
  until the binding results in a nil value."
  [bindings & body]
  `(loop []
     (when-let ~bindings
       ~@body
       (recur))))

(defn double->string
  "Converts a double to string without using exponential notation or loss of accuracy."
  [d]
  (when d (.format (DecimalFormat. "#.#####################") d)))

(defn rename-keys-with [m kmap merge-fn]
  "Returns the map with the keys in kmap renamed to the vals in kmap. Values of renamed keys for which
  there is already existing value will be merged using the merge-fn. merge-fn will be called with
  the original keys value and the renamed keys value."
  (let [rename-subset (select-keys m (keys kmap))
        renamed-subsets  (map (fn [[k v]]
                                (set/rename-keys {k v} kmap))
                              rename-subset)
        m-without-renamed (apply dissoc m (keys kmap))]
    (reduce #(merge-with merge-fn %1 %2) m-without-renamed renamed-subsets)))


(defn binary-search
  "Does a binary search between minv and maxv searching for an acceptable value. middle-fn should
  be a function taking two values and finding the midpoint. matches-fn should be a function taking a
  value along with the current recursion depth. matches-fn should return a keyword of :less-than,
  :greater-than, or :matches to indicate if the current value is an acceptable response."
  [minv maxv middle-fn matches-fn]
  (loop [minv minv
         maxv maxv
         depth 0]
    (let [current (middle-fn minv maxv)
          matches-result (matches-fn current minv maxv depth)]
      (case matches-result
        :less-than (recur current maxv (inc depth))
        :greater-than (recur minv current (inc depth))
        matches-result))))
