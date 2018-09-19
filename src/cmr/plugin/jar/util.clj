(ns cmr.plugin.jar.util
  (:require
    [clojure.string :as string]
    [taoensso.timbre :as log])
  (:import
    (clojure.lang Symbol)))

(defn resolve-fully-qualified-fn
  [^Symbol fqfn]
  (when fqfn
    (try
      (let [[name-sp fun] (mapv symbol (string/split (str fqfn) #"/"))]
        (require name-sp)
        (var-get (ns-resolve name-sp fun)))
      (catch  Exception _
        (log/warn "Couldn't resolve one or more of" fqfn)))))

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

(defn matched-coll
  [coll regex-str]
  (->> coll
       (map #(re-matches (re-pattern regex-str) %))
       (remove nil?)))

(defn matched-keys
  [hashmap regex-str]
  (->> hashmap
       keys
       (map #(re-matches (re-pattern regex-str) %))
       (remove nil?)))

(defn matched-vals
  [hashmap key-regex-str val-regex-str]
  (->> hashmap
       vec
       (map (fn [[k v]]
             (when (re-matches (re-pattern key-regex-str) k)
               (re-matches (re-pattern val-regex-str) v))))
       (remove nil?)))
