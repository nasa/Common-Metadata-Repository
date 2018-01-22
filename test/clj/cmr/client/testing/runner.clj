(ns cmr.client.testing.runner
  (:require
   [clojure.string :as string]
   [cmr.client.tests]
   [ltest.core :as ltest])
  (:gen-class))

(def tests-regex #"cmr\.client\.tests\..*")

(defn run-tests
  ([]
   (ltest/run-all-tests tests-regex))
  ([arg]
   (cond
    (coll? arg) (ltest/run-tests arg)
    (var? arg) (ltest/run-test arg))))

(defn print-header
  []
  (println)
  (println (string/join (repeat 80 "=")))
  (println "CMR Client Test Runner")
  (println (apply str (repeat 80 "=")))
  (println))

(defn -main
  "This can be run from `lein` in the following ways:

  * `lein run-tests unit`"
  [& args]
  (print-header)
  (case (keyword (first args))
    :unit (ltest/run-unit-tests tests-regex)
    :integration (ltest/run-integration-tests tests-regex)
    :system (ltest/run-system-tests tests-regex)
    (if (nil? args)
      (run-tests)
      (run-tests args))))
