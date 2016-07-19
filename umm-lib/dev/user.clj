(ns user
  (:require [cmr.common.dev.capture-reveal]
            [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            proto-repl.saved-values)
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]))

(defn reset []
  (refresh))

(println "Custom user.clj loaded.")
