(ns cmr.dev.env.manager.util
  (:require
    [clojure.java.io :as io]
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

(defn pushback-reader
  [io-stream]
  (new java.io.PushbackReader (io/reader io-stream)))
