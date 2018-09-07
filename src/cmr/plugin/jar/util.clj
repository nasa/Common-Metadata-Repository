(ns cmr.plugin.jar.util
  (:require
    [clojure.string :as string])
  (:import
    (clojure.lang Symbol)))

(defn resolve-fully-qualified-fn
  [^Symbol fqfn]
  (let [[name-sp fun] (mapv symbol (string/split (str fqfn) #"/"))]
    (require name-sp)
    (var-get (ns-resolve name-sp fun))))

(defn matches-coll?
  [coll regex-str]
  (->> coll
       (map #(re-matches (re-pattern regex-str) %))
       (some (complement nil?))))

(defn matches-key?
  [hashmap regex-str]
  (matches-coll? (keys hashmap) regex-str))

(defn matches-val?
  [hashmap regex-str]
  (matches-coll? (vals hashmap) regex-str))
