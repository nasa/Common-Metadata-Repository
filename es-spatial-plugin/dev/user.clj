(ns user
  (:require [clojure.pprint :refer (pprint pp)]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)])
  (:use [clojure.test :only [run-all-tests]]
        [clojure.repl]))

(defn reset []
  (refresh))

(println "Custom user.clj loaded.")