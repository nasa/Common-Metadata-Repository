(ns user
  (:require
   [clojure.tools.namespace.repl :refer (refresh)]
   [cmr.system-int-test.system :as s]
   [clojure.repl]
   [alex-and-georges.debug-repl]))

(defn reset []
  (s/stop)
  ; Refreshes all the code in repl
  (refresh))

(println "Custom user.clj loaded.")
