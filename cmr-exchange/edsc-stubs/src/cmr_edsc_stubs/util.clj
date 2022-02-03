(ns cmr-edsc-stubs.util
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.walk :refer [postwalk-replace]]))

(def local-token "mock-echo-system-token")
(def local-token-header {"authorization" local-token})

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

(defn kebab->snake
  [string-or-seq-data]
  (if (seq? string-or-seq-data)
    (map kebab->snake string-or-seq-data)
    (string/replace string-or-seq-data "-" "_")))

(defn snake->kebab
  [string-or-seq-data]
  (if (seq? string-or-seq-data)
    (map kebab->snake string-or-seq-data)
    (string/replace string-or-seq-data "_" "-")))

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
  (case mode
    [:camel :kebab] camel->kebab
    [:camel :snake] camel->snake
    [:kebab :snake] kebab->snake
    [:snake :kebab] snake->kebab
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
