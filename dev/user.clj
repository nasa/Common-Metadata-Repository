(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [system])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [config system/default-config
        s (system/create-system config)]
    (alter-var-root #'system
                    (constantly
                      (system/start s)))))


(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (system/stop s)))))

(defn reset []
  ; Stops the running code
  (stop)
  ; Refreshes all of the code and then restarts the system
  (refresh :after 'user/start))

(println "Custom user.clj loaded.")