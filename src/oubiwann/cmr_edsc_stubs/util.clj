(ns oubiwann.cmr-edsc-stubs.util
  (:require [clojure.java.io :as io]))

(defn files?
  [files-or-dirs]
  (filter #(.isFile %) files-or-dirs))

(defn get-files
  [dir]
  (->> dir
       (io/file)
       (file-seq)
       files?))
