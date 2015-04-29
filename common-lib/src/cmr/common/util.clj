(ns cmr.common.util
  "Utility functions that might be useful throughout the CMR."
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [camel-snake-kebab.core :as csk]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as w]
            [clojure.template :as template]
            [clojure.test :as test])
  (:import java.text.DecimalFormat))

(defmacro are2
  "Based on the are macro from clojure.test. Checks multiple assertions with a template expression.
  Wraps each tested expression in a testing block to identify what's being tested.
  See clojure.template/do-template for an explanation of templates.

  Example: (are2 [x y] (= x y)
                \"The most basic case with 1\"
                2 (+ 1 1)
                \"A more complicated test\"
                4 (* 2 2))
  Expands to:
           (do
              (testing \"The most basic case with 1\"
                (is (= 2 (+ 1 1))))
              (testing \"A more complicated test\"
                (is (= 4 (* 2 2)))))

  Note: This breaks some reporting features, such as line numbers."
  [argv expr & args]
  (if (or
        ;; (are2 [] true) is meaningless but ok
        (and (empty? argv) (empty? args))
        ;; Catch wrong number of args
        (and (pos? (count argv))
             (pos? (count args))
             (zero? (mod (count args) (inc (count argv))))))
    (let [testing-var (gensym "testing-msg")
          argv (vec (cons testing-var argv))]
      `(template/do-template ~argv (test/testing ~testing-var (test/is ~expr)) ~@args))
    (throw (IllegalArgumentException.
             "The number of args doesn't match are2's argv or testing doc string may be missing."))))

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

(defmacro defn-timed
  "Creates a function that logs how long it took to execute the body"
  [fn-name doc-string bindings & body]
  (when-not (and (string? doc-string) (vector? bindings))
    (throw (Exception. "defn-timed doesn't support different sets of args and must be used with a doc string.")))
  (let [fn-name-str (name fn-name)
        ns-str (str *ns*)]
    `(defn ~fn-name
       ~doc-string
       ~bindings
       (let [start# (System/currentTimeMillis)]
         (try
           ~@body
           (finally
             (let [elapsed# (- (System/currentTimeMillis) start#)]
               (debug (format
                        "Timed function %s/%s took %d ms." ~ns-str ~fn-name-str elapsed#)))))))))

(defn build-validator
  "Creates a function that will call f with it's arguments. If f returns any errors then it will
  throw a service error of the type given."
  [error-type f]
  (fn [& args]
    (when-let [errors (apply f args)]
      (when (seq errors)
        (errors/throw-service-errors error-type errors)))))

(defn apply-validations
  "Applies the arguments to each validation concatenating all errors and returning them"
  [validations & args]
  (reduce (fn [errors validation]
            (if-let [new-errors (apply validation args)]
              (concat errors new-errors)
              errors))
          []
          validations))

(defn compose-validations
  "Creates a function that will compose together a list of validation functions into a
  single function that will perform all validations together"
  [validation-fns]
  (partial apply-validations validation-fns))

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

(defn- compare-results-match?
  "Returns true if the given values are in order based on the given matches-fn, otherwise returns false."
  [matches-fn values]
  (->> (partition 2 1 values)
       (map #(apply compare %))
       (every? matches-fn)))

(defn greater-than?
  "Returns true if the given values are in descending order. This is similar to core/> except it uses
  compare function underneath and applies to other types other than just java.lang.Number."
  [& values]
  (compare-results-match? pos? values))

(defn less-than?
  "Returns true if the given values are in ascending order. This is similar to core/< except it uses
  compare function underneath and applies to other types other than just java.lang.Number."
  [& values]
  (compare-results-match? neg? values))
