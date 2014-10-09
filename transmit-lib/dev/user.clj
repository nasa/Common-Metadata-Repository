(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.transmit.config :as config]
            [cmr.common.dev.util :as d])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [sys (config/system-with-connections {} [:echo-rest])]
    (alter-var-root #'system (constantly sys)))
  (d/touch-user-clj))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system (constantly nil)))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(println "Custom cmr-tramsmit-lib user.clj loaded.")
