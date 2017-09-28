(ns cmr-edsc-stubs.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]))

(def local-token "mock-echo-system-token")
(def local-token-header {"echo-token" local-token})

(defn get-file
  ([file-path]
    (get-file file-path :data))
  ([file-path handler]
    (let [file-obj (io/resource file-path)]
      (case handler
        :obj file-obj
        :data (slurp file-obj)
        [:json :edn] (-> file-obj
                         (io/reader)
                         (json/parse-stream true))))))

(defn files?
  [files-or-dirs]
  (filter #(.isFile %) files-or-dirs))

(defn get-files
  [dir]
  (->> dir
       (io/file)
       (file-seq)
       files?))
