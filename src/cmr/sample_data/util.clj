(ns cmr.sample-data.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.sample-data.const :as const]))

(defn get-file
  ([file-path]
    (get-file file-path const/default-as-data))
  ([file-path handler]
    (let [file-obj (io/resource file-path)]
      (case handler
        :obj file-obj
        :data (slurp file-obj)
        [:json :edn] (-> file-obj
                         (io/reader)
                         (json/parse-stream true))))))

(defn get-dir
  [dir-path]
  (-> dir-path
      (io/resource)
      (.getFile)
      (io/file)))

(defn files?
  [files-or-dirs]
  (filter #(.isFile %) files-or-dirs))

(defn get-files
  [dir]
  (-> dir
      (get-dir)
      (file-seq)
      files?))
