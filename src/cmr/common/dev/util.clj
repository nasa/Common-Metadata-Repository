(ns cmr.common.dev.util
  "This contains utility functions for development"
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import java.awt.datatransfer.StringSelection
           java.awt.datatransfer.Clipboard
           java.awt.Toolkit))

(def saved-item
  "An atom to use for stashing stuff for examination in the repl"
  (atom nil))

(defn save-for-examination
  "Saves whatever is passed to it for examination later in the repl."
  [& stuff]
  (reset! saved-item stuff))

(defn examine-saved
  "Returns the item saved for examination"
  []
  (deref saved-item))

(defn touch-file
  [file]
  (future
    (try
      (sh/sh "touch" file)
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

(defn speak
  "Says the specified text outloud."
  [text]
  (sh/sh "say" "-v" "Victoria" text))

(defn copy-to-clipboard
  "Copies the string into the clipboard and returns the string"
  [s]
  (let [clipboard (.getSystemClipboard (Toolkit/getDefaultToolkit))]
    (.setContents clipboard (StringSelection. s) nil))
  s)

(defn message->regex
  "Converts an expected message into the a regular expression that matches the exact string.
  Handles escaping special regex characters"
  [msg]
  (-> msg
      (str/replace #"\[" "\\\\[")
      (str/replace #"\]" "\\\\]")
      re-pattern))