(ns cmr.client.testing.runner
  #_(:require
   [ltest.core :as ltest])
  #_(:gen-class))

#_(defn run-tests
  ([]
   (ltest/run-all-tests #"cmr.client.tests.*"))
  ([arg]
   (cond
    (coll? arg) (ltest/run-tests arg)
    (var? arg) (ltest/run-test arg))))

#_(defn -main
  [& args]
  (println "main args:" args)
  (if (nil? args)
    (run-tests)
    (run-tests args)))
