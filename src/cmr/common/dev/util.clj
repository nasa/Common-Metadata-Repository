(ns cmr.common.dev.util
  "This contains utility functions for development"
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]))

(defn touch-file
  [file]
  (future
    (try
      (clojure.java.shell/sh "touch" file)
      (catch Throwable e
        (println "Error touch" file)
        (.printStackTrace e))))
  nil)


(defn touch-user-clj
  "Touches dev/user.clj to help avoid cases where file changes are not caught by
  clojure.tools.namespace refresh."
  []
  (touch-file "dev/user.clj"))

(defn touch-files-in-dir
  "Touches all top level files in the folder."
  [dir]
  (let [d (io/file dir)
        files (seq (.listFiles d))]
    (dorun (map #(-> % str touch-file) (filter #(not (.isDirectory ^java.io.File %)) files)))))

(comment
(touch-files-in-dir ".")

)