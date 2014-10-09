(ns cmr.common.test.test-check-ext
  (:require [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [clj-time.coerce :as c]
            [clojure.test.check.clojure-test]
            [clojure.test.check]
            [clojure.test]
            [clojure.pprint])
  (:import java.util.Random))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The following two functions were copy and pasted from the clojure test.check library to fix a small
;; issue relating to the printing out of failed specs.
;; See http://dev.clojure.org/jira/browse/TCHECK-18
;; I have also changed the argument just after name, options, to be a map of options that can be passed in.

(def ^:dynamic  *default-test-count* 100)

(defn- assert-check
  [{:keys [result shrunk fail] :as m} {:keys [printer-fn]}]
  (println (pr-str m))

  (when printer-fn
    (when fail
      (println "-----------------------------------------------------------------------")
      (apply printer-fn :first-fail fail))
    (when (:smallest shrunk)
      (println "-----------------------------------------------------------------------")
      (apply printer-fn :shrunken (:smallest shrunk))))

  (if (instance? Throwable result)
    (throw result)
    (clojure.test/is result)))

(defmacro defspec
  "Defines a new clojure.test test var that uses `quick-check` to verify
  [property] with the given [args] (should be a sequence of generators).
  You can call the function defined as [name]
  with no arguments to trigger this test directly (i.e.  without starting a
  wider clojure.test run), or with a single argument that will override
  times set in options.

  Valid options:
  * times: The number of times to run the test.
  * printer-fn: A function that will be called with the first failure and the shrunken failure to
  print out extra information. It should take "
  ([name property]
   `(defspec ~name {:times ~*default-test-count*} ~property))

  ([name options property]
   (let [options (if (number? options)
                   {:times options}
                   options)
         {:keys [times]} options]
     `(do
        (defn ~(vary-meta name assoc
                          ::defspec true
                          :test `#(#'cmr.common.test.test-check-ext/assert-check (assoc (~name) :test-var (str '~name)) ~options))
          ([] (~name ~times))
          ([times# & {:keys [seed# max-size#] :as quick-check-opts#}]
           (apply
             clojure.test.check/quick-check
             times#
             (vary-meta ~property assoc :name (str '~property))
             (flatten (seq quick-check-opts#)))))))))


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
(defn choose-double
  "Creates a generator that returns values between lower and upper inclusive. Min and max values must
  integers and the must be separated by more than 2."
  [minv maxv]
  {:pre [(< minv maxv)
         (> (Math/abs (double (- maxv minv))) 2)
         (= (int maxv) maxv)
         (= (int minv) minv)]}
  (gen/fmap (fn [[i denominator]]
              ;; Adds an integer to a fractional component. The fractional component should shrink to 0
              (+ i (/ 1.0 denominator)))
            (gen/tuple (gen/choose (inc minv) (dec maxv))
                       (gen/such-that (complement zero?) gen/int))))

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

(def http-schemes
  "Some URL schemes."
  (gen/elements ["ftp" "http" "https" "file"]))

(def domain-exts
  "Some url domain extenstions."
  (gen/elements ["com" "org" "gov"]))

(def file-exts
  "Some file extensions."
  (gen/elements ["jpg" "png" "gif" "tiff" "txt"]))

(def file-url-string
  "A generator that will create a simple URL string for a file resource."
  (gen/fmap (fn [[scheme domain domain-ext file-name-base file-ext]]
              (str scheme "://" domain "." domain-ext "/" file-name-base "." file-ext))
            (gen/tuple http-schemes
                       (gen/not-empty gen/string-alpha-numeric)
                       domain-exts
                       (gen/not-empty gen/string-alpha-numeric)
                       file-exts)))
