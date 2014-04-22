(ns cmr.common.test.test-check-ext
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [clj-time.coerce :as c]
            [clojure.test.check.clojure-test]
            [clojure.test])
  (:import java.util.Random))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The following two functions were copy and pasted from the clojure test.check library to fix a small
;; issue relating to the printing out of failed specs.
;; See http://dev.clojure.org/jira/browse/TCHECK-18
;; This defspec can be used until the test.check issue is fixed and released.

(defn- assert-check
  [{:keys [result] :as m}]
  (println (pr-str m))
  (if (instance? Throwable result)
    (throw result)
    (clojure.test/is result)))

(defmacro defspec
  "Defines a new clojure.test test var that uses `quick-check` to verify
  [property] with the given [args] (should be a sequence of generators),
  [default-times] times by default.  You can call the function defined as [name]
  with no arguments to trigger this test directly (i.e.  without starting a
  wider clojure.test run), or with a single argument that will override
  [default-times]."
  ([name property]
   `(defspec ~name ~clojure.test.check.clojure-test/*default-test-count* ~property))

  ([name default-times property]
   `(do
      ;; consider my shame for introducing a cyclical dependency like this...
      ;; Don't think we'll know what the solution is until clojure.test.check
      ;; integration with another test framework is attempted.
      (require 'clojure.test.check)
      (defn ~(vary-meta name assoc
                        ::defspec true
                        :test `#(#'cmr.common.test.test-check-ext/assert-check (assoc (~name) :test-var (str '~name))))
        ([] (~name ~default-times))
        ([times# & {:keys [seed# max-size#] :as quick-check-opts#}]
           (apply
             clojure.test.check/quick-check
             times#
             (vary-meta ~property assoc :name (str '~property))
             (flatten (seq quick-check-opts#))))))))


;; End of copy and pasted section
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn optional
  "Returns either nil or the given generator. This should be used for optional fields"
  [generator]
  (gen/one-of [(gen/return nil) generator]))

(defn nil-if-empty
  "Creates a generator that takes another sequence generator. If the sequence produced is empty then
  a nil value will be returned"
  [gen]
  (gen/fmap #(when (not (empty? %))
               %)
            gen))

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

(defn- not-whitespace
  "Takes a string generator and returns a new string generator that will not contain only whitespace"
  [generator]
  (gen/such-that #(not (re-matches #"^\s+$" %)) generator))

(defn string-ascii
  "Like the clojure.test.check.generators/string-ascii but allows a min and max length to be set"
  ([]
   gen/string-ascii)
  ([min-size max-size]
   (not-whitespace (gen/fmap s/join (gen/vector gen/char-ascii min-size max-size)))))

(defn string-alpha-numeric
  "Like the clojure.test.check.generators/string-alpha-numeric but allows a min and max length to be set"
  ([]
   gen/string-alpha-numeric)
  ([min-size max-size]
   (gen/fmap s/join (gen/vector gen/char-alpha-numeric min-size max-size))))

(def date-time
  "A generator that will return a Joda DateTime between 1970-01-01T00:00:00.000Z and 2114-01-01T00:00:00.000Z"
  (gen/fmap c/from-long (gen/choose 0 4544208000000)))
