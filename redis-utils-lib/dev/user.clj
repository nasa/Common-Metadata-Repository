(ns user
  (:require
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [cmr.common.test.runners.ltest :as ltest])
  (:use
   [alex-and-georges.debug-repl]
   [clojure.test :only [run-all-tests]]
   [clojure.repl]
   [cmr.common.dev.capture-reveal]))


(defn reset []

  ; Refreshes all of the code in repl
  (refresh))

(println "Custom user.clj loaded.")
