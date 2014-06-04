(ns cmr.search.runner
  (:require [cmr.search.system :as system]
            [clojure.tools.cli :refer [cli]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [cmr.common.api.web-server :as web]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.search.api.routes :as routes])
  (:gen-class))

(defn -main
  "Starts the App."
  [& args]
  (let [system (system/start (system/create-system))]
    (info "Running...")))
