(ns user
  (:require
   [clojure.tools.namespace.repl :refer (refresh)]
   [cmr.common.dev.capture-reveal]
   [cmr.common.dev.util :as dev-util]))

(defn reset []
  ;; Force the JSON Schemas to be reloaded since clojure tools namespace can't tell that those
  ;; files have been modified.
  (dev-util/touch-file "src/cmr/umm_spec/json_schema.clj")
  (refresh))

(println "umm-spec user.clj loaded.")
