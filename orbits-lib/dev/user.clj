(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            ;; Make Proto REPL lib properties available.
            [proto-repl.saved-values])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]))

(defn reset []
  (refresh))

(println "Custom user.clj loaded.")
