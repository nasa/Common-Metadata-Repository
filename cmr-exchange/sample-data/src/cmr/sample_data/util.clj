(ns cmr.sample-data.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.sample-data.const :as const])
  (:import
   (java.util.zip ZipFile)))

(def as-data-handlers
  {:obj identity
   :data slurp
   [:json :edn] #(-> %
                     (io/reader)
                     (json/parse-stream true))})

(defn get-file
  ([file-path]
    (get-file file-path const/default-handler-key))
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

(defn get-dir-files
  [dir]
  (->> dir
       (get-dir)
       file-seq
       files?))

(defn parse-jar-file
  [dir]
  (-> (io/resource dir)
      (.getPath)
      (string/split #"!")
      first
      (string/split #"file:")
      last))

(defn jar-file?
  [entry]
  (not (.isDirectory entry)))

(defn jar-file-starts-with?
  [jar-file dir]
  (-> jar-file
      (.getName)
      (string/starts-with? dir)))

(defn get-jar-files
  [dir]
  (let [jar-file (parse-jar-file dir)
        zip-file (new ZipFile jar-file)
        entries (enumeration-seq (.entries zip-file))]
    (->> (.entries zip-file)
         enumeration-seq
         (filter jar-file?)
         (filter #(jar-file-starts-with? % dir))
         (map #(io/resource (.getName %))))))

(defn get-dir-or-jar-files
  [dir]
  (case (.getProtocol (io/resource dir))
    "file" (get-dir-files dir)
    "jar" (get-jar-files dir)))

(defn get-files
  ([dir]
    (get-files dir :obj))
  ([dir handler-key]
    (->> dir
         (get-dir-or-jar-files)
         (map (as-data-handlers handler-key)))))
