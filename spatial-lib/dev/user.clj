(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.common.lifecycle :as lifecycle])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]
        proto-repl.saved-values
        [cmr.common.dev.capture-reveal]))


(defn start
  "Starts the current development system."
  [])


(defn stop
  "Shuts down and destroys the current development system."
  [])

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))
(println "Custom user.clj loaded.")
