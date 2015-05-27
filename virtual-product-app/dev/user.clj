(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [clojure.test :refer (run-all-tests)]
            [clojure.repl :refer :all]
            [alex-and-georges.debug-repl :refer (debug-repl)]
            [cmr.virtual-product.system :as system]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.dev.util :as d]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [s (system/create-system)]
    (alter-var-root #'system
                    (constantly
                      (system/start s))))
  (d/touch-user-clj))

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

(info "Custom virtual-product user.clj loaded.")
