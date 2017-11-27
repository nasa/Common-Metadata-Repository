(ns cmr.dev.env.manager.process
  (:require
    [clojure.core.async :as async]
    [clojure.java.io :as io]
    [me.raynes.conch.low-level :as shell]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *byte-buffer-size* 1024)
(def ^:dynamic *channel-buffer-size* (* 10 1024))
(def ^:dynamic *read-stream-delay* 5000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn- read-stream
  [^java.io.InputStream input-stream channel]
  (let [bytes (make-byte-array)]
    (async/go-loop [stream input-stream
                    bytes-read (.read stream bytes)]
      (when (pos? bytes-read)
        (async/>! channel (bytes->ascii bytes))
        (recur stream (.read stream bytes))))))

(defn channel-log
  [ch log-type]
  (async/go-loop []
    (when-let [v (async/<! ch)]
      (case log-type
        :error (log/error v)
        :debug (log/debug v)
        :info (log/info v)))
    (recur)))

(defn log-error
  [ch]
  (channel-log ch :error))

(defn log-info
  [ch]
  (channel-log ch :info))

(defn log-debug
  [ch]
  (channel-log ch :debug))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log-process
  [process out-chan err-chan]
  (read-stream (:out process) out-chan)
  (log-debug out-chan)
  (read-stream (:err process) err-chan)
  (log-error err-chan))

(defn spawn!
  [& args]
  (let [process (apply shell/proc args)
        out-chan (async/chan *channel-buffer-size*)
        err-chan (async/chan *channel-buffer-size*)]
    (log-process process out-chan err-chan)
    (merge process {:out-channel out-chan
                    :err-channel err-chan})))

(defn terminate!
  [process-data]
  (let [exit-code (future (shell/exit-code process-data))]
    (shell/destroy process-data)
    (async/close! (:out-channel process-data))
    (async/close! (:err-channel process-data))
    @exit-code))
