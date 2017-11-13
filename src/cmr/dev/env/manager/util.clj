(ns cmr.dev.env.manager.util
  (:require
    [clojure.core.async :as async]
    [clojure.java.shell :as shell]
    [taoensso.timbre :as log]))

(declare deep-merge)

(defmulti merge-val
  (fn [a b]
    (type a)))

(defmethod merge-val clojure.lang.PersistentVector
  [a b]
  (concat a b))

(defmethod merge-val clojure.lang.PersistentArrayMap
  [a b]
  (deep-merge a b))

(defmethod merge-val :default
  [a b]
  b)

(defn deep-merge
  ""
  [data1 data2]
  (merge-with merge-val data1 data2))

(defn shell!
  [& args]
  (log/trace "shell! args:" args)
  (let [results (apply shell/sh args)
        out (:out results)
        err (:err results)]
    (when (seq out)
      (log/debug out))
    (when (seq err)
      (log/error err))))

(defn spawn!
  [& args]
  (async/thread (apply shell! args)))
