(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.search.system :as system]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.search.api.routes :as routes]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.common.test.repeat-last-request :as repeat-last-request :refer (repeat-last-request)])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

; See http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded
; for information on why this file is setup this way

(def system nil)

(defn start
  "Starts the current development system."
  []
  (let [web-server (web/create-web-server 3003 (repeat-last-request/wrap-api routes/make-api))
        log (log/create-logger)
        search-index (idx/create-elastic-search-index "localhost" 9200)
        s (system/create-system log web-server search-index)]
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
