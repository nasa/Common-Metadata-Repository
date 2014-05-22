(ns cmr.common.dev.util
  "This contains utility functions for development"
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.awt.datatransfer.StringSelection
           java.awt.datatransfer.Clipboard
           java.awt.Toolkit))

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

(defn copy-to-clipboard
  "Copies the string into the clipboard"
  [s]
  (let [clipboard (.getSystemClipboard (Toolkit/getDefaultToolkit))]
    (.setContents clipboard (StringSelection. s) nil)))

(defn message->regex
  "Converts an expected message into the a regular expression that matches the exact string.
  Handles escaping special regex characters"
  [msg]
  (-> msg
      (str/replace #"\[" "\\\\[")
      (str/replace #"\]" "\\\\]")
      re-pattern))