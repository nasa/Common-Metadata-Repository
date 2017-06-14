(ns cmr.common.test.test-check-ext
  (:require
   [clj-time.coerce :as c]
   [clojure.string :as s]
   [clojure.pprint]
   [clojure.test]
   [clojure.test.check :as test-check]
   [clojure.test.check.clojure-test]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [com.gfredericks.test.chuck.clojure-test :as chuck])
  (:import java.util.Random))

(defn require-proto-repl-saved-values
  "Tries to require proto repl saved values safely. Does nothing if not available."
  []
  (try
   (require 'proto-repl.saved-values)
   (catch Throwable _)))

(defn save-last-failed-values
  "Saves the bindings map captured from the given namespace into the Proto REPL saved values atom so
   it can be displayed like normal saved values."
  [name current-ns bindings-map]
  (when (find-ns 'proto-repl.saved-values)
    (let [saved-values-atom (var-get (find-var 'proto-repl.saved-values/saved-values-atom))
          saved-values {:id (.toString (java.util.UUID/randomUUID))
                        :the-ns current-ns
                        :values bindings-map}]
      (swap! saved-values-atom assoc name [saved-values]))))

(defn binding-values
  "Macro helper. Takes a binding vector and returns a map of code to quote the binding name to
   the value of the binding. Used to create a map of binding names to values."
  [bindings]
  (into {} (for [[binding-name _] (partition 2 bindings)]
             [`(quote ~binding-name) binding-name])))

(defmacro qc-and-report-exception
  [name final-reports tests bindings & body]
  `(chuck/report-exception-or-shrunk
    (test-check/quick-check
      ~tests
      (prop/for-all ~bindings
        (let [reports# (chuck/capture-reports ~@body)]
          (swap! ~final-reports chuck/save-to-final-reports reports#)

          ;; CODE Added to original qc-and-report-exception function
          ;; Saves the values into the Proto REPL saved values atom so they can be displayed.
          (when-not (chuck/pass? reports#)
            (save-last-failed-values ~name (symbol (str *ns*)) ~(binding-values bindings)))

          (chuck/pass? reports#))))))

(defmacro qc-and-report-exception-with-seed
  "This macro re-runs a failed test so that it can capture the information to produce a failed test report.
   The only difference between this function and the one above is that this one uses a seed value so that
   the generated values of a UMM record are repeatable."
  [name seed final-reports tests bindings & body]
  `(chuck/report-exception-or-shrunk
    (test-check/quick-check
      ~tests
      (prop/for-all ~bindings
        (let [reports# (chuck/capture-reports ~@body)]
          (swap! ~final-reports chuck/save-to-final-reports reports#)

          ;; CODE Added to original qc-and-report-exception function
          ;; Saves the values into the Proto REPL saved values atom so they can be displayed.
          (when-not (chuck/pass? reports#)
            (save-last-failed-values ~name (symbol (str *ns*)) ~(binding-values bindings)))

          (chuck/pass? reports#)))
      :seed ~seed)))

(defmacro checking
  "Copied from com.gfredericks.test.chuck.clojure-test so that we can add code to capture the generated
   values.

  ORIGINAL DESCRIPTION:
  A macro intended to replace the testing macro in clojure.test with a
  generative form. To make (testing \"doubling\" (is (= (* 2 2) (+ 2 2))))
  generative, you simply have to change it to
  (checking \"doubling\" 100 [x gen/int] (is (= (* 2 x) (+ x x)))).

  For more details on this code, see http://blog.colinwilliams.name/clojure/testing/2015/01/26/alternative-clojure-dot-test-integration-with-test-dot-check.html"
  [name tests bindings & body]
  `(do
     (require-proto-repl-saved-values)
     (chuck/-testing
      ~name
      (fn []
        (let [final-reports# (atom [])]
          (qc-and-report-exception ~name final-reports# ~tests ~bindings ~@body)
          (doseq [r# @final-reports#]
            (chuck/-report r#)))))))

(defmacro checking-with-seed
  "Copied from com.gfredericks.test.chuck.clojure-test so that we can add code to capture the generated
   values.

  ORIGINAL DESCRIPTION:
  A macro intended to replace the testing macro in clojure.test with a
  generative form. To make (testing \"doubling\" (is (= (* 2 2) (+ 2 2))))
  generative, you simply have to change it to
  (checking \"doubling\" 100 [x gen/int] (is (= (* 2 x) (+ x x)))).

  For more details on this code, see http://blog.colinwilliams.name/clojure/testing/2015/01/26/alternative-clojure-dot-test-integration-with-test-dot-check.html"
  [name tests seed bindings & body]
  `(do
     (require-proto-repl-saved-values)
     (chuck/-testing
      ~name
      (fn []
        (let [final-reports# (atom [])]
          (qc-and-report-exception-with-seed ~name ~seed final-reports# ~tests ~bindings ~@body)
          (doseq [r# @final-reports#]
            (chuck/-report r#)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DEPRECATED
;; The following functions until END OF DEPRECATED are deprecated in lieu of the above checking code


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The following two functions were copy and pasted from the clojure test.check library to fix a small
;; issue relating to the printing out of failed specs.
;; See http://dev.clojure.org/jira/browse/TCHECK-18
;; I have also changed the argument just after name, options, to be a map of options that can be passed in.

(def ^:dynamic  *default-test-count* 100)

(defn print-value-in-comment
  "Prints a clojure value to the repl inside a comment block. This helps with code formatting in Sublime"
  [v]
  (println (pr-str (list 'comment v))))

(defn print-failing-value
  [_ & v]
  (print-value-in-comment (concat (list 'def 'failing-value) v)))

(defn- assert-check
  [{:keys [result shrunk fail] :as m} {:keys [printer-fn]}]
  (let [printer-fn (or printer-fn print-failing-value)]

    (print-value-in-comment m)

    (when (:smallest shrunk)
      (println "-----------------------------------------------------------------------")
      (println "Smallest failing value:")
      (apply printer-fn :shrunken (:smallest shrunk)))

    (if (instance? Throwable result)
      (throw result)
      (clojure.test/is result))))

(defmacro defspec
  "DEPRECATED. Use deftest with checking function.
  Defines a new clojure.test test var that uses `quick-check` to verify
  [property] with the given [args] (should be a sequence of generators).
  You can call the function defined as [name]
  with no arguments to trigger this test directly (i.e.  without starting a
  wider clojure.test run), or with a single argument that will override
  times set in options.

  Valid options:
  * times: The number of times to run the test.
  * printer-fn: A function that will be called with the first failure and the shrunken failure to
  print out extra information."
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


;; END OF DEPRECATED
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn optional
  "Returns either nil or the given generator. This should be used for optional
  fields."
  [generator]
  (gen/one-of [(gen/return nil) generator]))

(defn nil-if-empty
  "Creates a generator that takes another sequence generator. If the sequence
  produced is empty then a nil value will be returned"
  [gen]
  (gen/fmap #(when (seq %) %) gen))

(defn model-gen
  "Creates a generator for models. Takes the constructor function for the model
  and generators for each argument."
  [constructor & args]
  (gen/fmap (partial apply constructor) (apply gen/tuple args)))

(defn non-empty-obj-gen
  "Returns a generator which returns nil instead of empty maps."
  [g]
  (gen/fmap (fn [x]
              (when (some some? (vals x))
                x))
            g))

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
