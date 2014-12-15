(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.common.lifecycle :as lifecycle])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]
        [cmr.common.dev.capture-reveal]))

(def vdd-server nil)

(defn start
  "Starts the current development system."
  []
  (let [viz-server (viz-helper/create-viz-server)]
    (alter-var-root #'vdd-server
                    (constantly
                      (lifecycle/start viz-server nil)))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'vdd-server
    (fn [s] (when s (lifecycle/stop s nil) nil))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))
(println "Custom user.clj loaded.")
