(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.system-int-test.system :as s]
            [cmr.common.config :as cfg])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))


(defn reset []

  (s/stop)

  ; Refreshes all of the code in repl
  (refresh))

(println "Custom user.clj loaded.")
