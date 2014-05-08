(ns cmr.common.util
  "Utility functions that might be useful throughout the CMR."
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [camel-snake-kebab :as csk]))

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
      ~@body

      (info ~taskname " completed without exception")

      (catch Throwable e#
        (error e# "Exception in " ~taskname)
        (throw e#))
      (finally
        (info ~taskname " complete.")))))

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

(defn remove-nil-keys
  "Removes keys mapping to nil values in a map.
  From http://stackoverflow.com/questions/3937661/remove-nil-values-from-a-map"
  [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))


(defn map-keys [f m]
  "Maps f over the keys in map m and updates all keys with the result of f.
  This is a recommended function from the Camel Snake Kebab library."
  (when m
    (letfn [(mapper [[k v]]
                    [(f k)
                     (if (map? v)
                       (map-keys f v)
                       v)])]
      (into {} (map mapper m)))))

(defn map-keys->snake_case
  "Converts map keys to snake_case."
  [m]
  (map-keys csk/->snake_case_keyword m))

(defn map-keys->kebab-case
  "Converts map keys to kebab-case"
  [m]
  (map-keys csk/->kebab-case-keyword m))

;; TODO - remove these comments after review
;; See: http://rosettacode.org/wiki/Determine_if_a_string_is_numeric#Clojure
;; Selected this function to avoid usage of try/catch block
;; Changed function name from numeric? to numeric-val? to avoid confusion with number?
;; This works with any sequence of characters, not just Strings, e.g.:
;; (numeric-val? [\1 \2 \3])  ;; yields logical true
;; (numeric-val? "c9")  ;; yields logical false
;; (numeric-val? ""), (numeric-val? nil) ;; yields nil
;; (numeric-val? "12.4e05") ;; yields false
;; (numeric-val? "-2.02") ;; yeilds true
(defn numeric-val? [s]
  "Returns true if supplied value is a number, false if value is alphanumeric and
  nil for empty string and nil values"
  (if-let [s (seq s)]
    (let [s (if (= (first s) \-) (next s) s)
          s (drop-while #(Character/isDigit %) s)
          s (if (= (first s) \.) (next s) s)
          s (drop-while #(Character/isDigit %) s)]
      (empty? s))))



