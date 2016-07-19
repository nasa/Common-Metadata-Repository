(ns user
  (:require [cmr.common.dev.capture-reveal]
            [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [cmr.common.dev.util :as dev-util]
            proto-repl.saved-values)
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        [alex-and-georges.debug-repl]))

(defn reset []
  ;; Force the JSON Schemas to be reloaded since clojure tools namespace can't tell that those
  ;; files have been modified.
  (dev-util/touch-file "src/cmr/umm_spec/json_schema.clj")
  (refresh))

(println "umm-spec user.clj loaded.")
