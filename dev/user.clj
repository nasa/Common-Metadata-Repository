(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [vdd-core.core :as vdd]
            [earth.driver :as earth-viz]
            [common-viz.util :as c])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]))

(def vdd-server nil)

(def viz-config
  (-> (vdd/config)
      (assoc :plugins ["earth" "flatland"])))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'vdd-server
                  (constantly
                    (vdd/start-viz viz-config))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'vdd-server
    (fn [s] (when s (vdd/stop-viz s)))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(defn reload-coffeescript []
  (do
    (println "Compiling coffeescript")
    (println (c/compile-coffeescript (:config vdd-server)))
    (vdd/data->viz {:cmd :reload})))

(println "Custom user.clj loaded.")
