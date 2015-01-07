(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]
        ;; Needed to make debug-repl available
        [alex-and-georges.debug-repl]))

(defn reset []
  (refresh))

(println "Custom user.clj loaded.")
