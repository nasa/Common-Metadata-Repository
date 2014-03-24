(ns cmr.common.dev.util
  "This contains utility functions for development"
  (:require [clojure.java.shell :as sh]))

(defn touch-user-clj
  "Touches dev/user.clj to help avoid cases where file changes are not caught by
  clojure.tools.namespace refresh."
  []
  (future
    (try
      (clojure.java.shell/sh "touch" "dev/user.clj")
      (catch Throwable e
        (println "Error touch user.clj")
        (.printStackTrace e))))
  nil)