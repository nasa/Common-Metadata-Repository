(ns cmr.process.manager.util
  (:require
    [clojure.java.io :as io]
    [me.raynes.conch.low-level :as shell]))

(def ^:dynamic *byte-buffer-size* 1024)

(defn newline?
  ""
  [byte]
  (= (char byte) \newline))

(defn bytes->ascii
  ""
  [bytes]
  (.trim (new String bytes "US-ASCII")))

(defn bytes->utf8
  ""
  [bytes]
  (.trim (new String bytes "UTF-8")))

(defn bytes->int
  ""
  [bytes]
  (-> bytes
      (bytes->ascii)
      (Integer/parseInt)))

(defn str->bytes
  ""
  [str]
  (.getBytes str))

(defn str->stream
  ""
  [str]
  (io/input-stream (str->bytes str)))

(defn make-byte-array
  ([]
    (make-byte-array *byte-buffer-size*))
  ([buffer-size]
    (make-array Byte/TYPE buffer-size)))

(defn get-cmd-output
  [& cmd-args]
  (shell/stream-to-string (apply shell/proc cmd-args) :out))
