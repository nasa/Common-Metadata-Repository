(ns cmr-edsc-stubs.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk-replace]]))

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

(defn camel->
  [regex string-format string-data]
  (-> string-data
      (string/replace regex string-format)
      (string/lower-case)))

(defn camels->
  [regex string-format seq-strings]
  (map (comp keyword
             (partial camel-> regex string-format)
             name)
       seq-strings))

(defn camel->kebab
  [string-or-seq-data]
  (let [regex #"([^-A-Z])([A-Z])"
        string-format "$1-$2"]
    (if (seq? string-or-seq-data)
      (camels-> regex string-format string-or-seq-data)
      (camel-> regex string-format string-or-seq-data))))

(defn camel->snake
  [string-or-seq-data]
  (let [regex #"([^_A-Z])([A-Z])"
        string-format "$1_$2"]
    (if (seq? string-or-seq-data)
      (camels-> regex string-format string-or-seq-data)
      (camel-> regex string-format string-or-seq-data))))

(defn lookup-converter
  [mode]
  (println "Mode:" mode)
  (case mode
    [:camel :kebab] camel->kebab
    [:camel :snake] camel->snake
    identity))

(defn get-all-keys
  [data]
  (concat (keys data)
          (mapcat (fn [[k v]] (if (map? v) (get-all-keys v))) data)))

(defn convert-keys
  [mode data]
  (let [orig-keys (get-all-keys data)
        converter-fn (lookup-converter mode)]
    (postwalk-replace (zipmap orig-keys (converter-fn orig-keys)) data)))
