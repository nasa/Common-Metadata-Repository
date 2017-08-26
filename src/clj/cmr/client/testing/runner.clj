(ns cmr.client.testing.runner
  (:require [ltest.core :as ltest])
  (:gen-class))

(defn run-tests
  ([]
   (ltest/run-all-tests #"cmr.client.tests.*"))
  ([arg]
   (cond
    (coll? arg) (ltest/run-tests arg)
    (var? arg) (ltest/run-test arg))))

(defn -main
  [& args]
  (println "main args:" args)
  (if (nil? args)
    (run-tests)
    (run-tests args)))
