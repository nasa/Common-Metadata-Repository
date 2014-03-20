(ns user
  "user is the default namespace of the REPL. This defines helper functions for starting and
  stopping the application from the REPL."
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.ingest.system :as system]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.ingest.data.mdb :as metadata-db]
            [cmr.ingest.data.indexer :as indexer]
            [cmr.ingest.api.routes :as routes])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

; See http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded
; for information on why this file is setup this way

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [web-server (web/create-web-server 3002 routes/make-api)
        db (metadata-db/create)
        idx-db (indexer/create)
        log (log/create-logger)
        s (system/create-system log db idx-db web-server)]
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

