(ns cmr.sample-data.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.sample-data.const :as const]))

(def as-data-handlers
  {:obj identity
   :data slurp
   [:json :edn] #(-> %
                     (io/reader)
                     (json/parse-stream true))})

(defn get-file
  ([file-path]
    (get-file file-path const/default-as-data)) ; XXX change to default-handler-key
  ([file-path handler-key]
    (let [file-obj (io/resource file-path)
          handler (as-data-handlers handler-key)]
      (handler file-obj))))

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
  ([dir]
    (get-files dir :obj))
  ([dir handler-key]
    (->> dir
         (get-dir)
         (file-seq)
         files?
         (map (as-data-handlers handler-key)))))
