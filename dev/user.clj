(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.metadata-db.system :as system]
            [taoensso.timbre :refer (debug info warn error)]
            [cmr.common.lifecycle :as lifecycle])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]))

; See http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded
; for information on why this file is setup this way

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [config (system/map->Config {:port 3000})
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

(info "Custom user.clj loaded.")
