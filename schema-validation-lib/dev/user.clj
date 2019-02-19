(ns user
  (:require
   ;; Needed to make debug-repl available
   [alex-and-georges.debug-repl]
   [clojure.pprint :refer [pprint pp]]
   [clojure.repl]
   [clojure.test :only [run-all-tests]]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   ;; Make Proto REPL lib properties available.
   [proto-repl.saved-values]))

(defn reset []
  (refresh))

(println "Custom user.clj loaded.")
