(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.ingest.system :as system]
            [cmr.common.log :refer (debug info warn error)])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

; See http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded
; for information on why this file is setup this way

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [s (system/create-system)]
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

