(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.config :as cfg])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(defn use-local-vm-ports
  "Sets config values so that this can run tests against applications deployed in a VM."
  []
  (cfg/set-config-value! :metadata-db-port 5122)
  (cfg/set-config-value! :ingest-port 5123)
  (cfg/set-config-value! :search-port 5124)
  (cfg/set-config-value! :indexer-port 5125)
  (cfg/set-config-value! :index-set-port 5126))

(defn start
  []

  ;; Uncomment this and below in reset to use local VM ports
  ; (use-local-vm-ports)

  )

(defn reset []
  (d/reset-uniques)

  ;; Due to reloading oddities it may be necessary to uncomment this as well.
  ; (use-local-vm-ports)

  ; Refreshes all of the code in repl
  (refresh :after 'user/start))

(println "Custom user.clj loaded.")
