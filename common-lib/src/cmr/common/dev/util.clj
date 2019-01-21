(ns cmr.common.dev.util
  "This contains utility functions for development"
  (:require
   [clojure.java.shell :as sh]
   [clojure.java.io :as io])
  (:import
   (java.awt.datatransfer Clipboard StringSelection)
   (java.awt Toolkit)
   (java.io File)))

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
        files-or-directories (.listFiles d)
        files (remove (fn [^File file]
                        (.isDirectory file))
                      files-or-directories)]
    (dorun (map #(-> % str touch-file) files))))

(defn speak
  "Says the specified text outloud."
  ([text]
   (speak "Victoria" text))
  ([voice text]
   (sh/sh "say" "-v" voice text)))

(defn copy-to-clipboard
  "Copies the string into the clipboard and returns the string"
  [s]
  (let [clipboard (.getSystemClipboard (Toolkit/getDefaultToolkit))]
    (.setContents clipboard (StringSelection. s) nil))
  s)
