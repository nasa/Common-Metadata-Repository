(ns user
  (:require [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.system-int-test.system :as s])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(defn reset []
  (s/stop)
  ; Refreshes all of the code in repl
  (refresh))

(println "Custom user.clj loaded.")
