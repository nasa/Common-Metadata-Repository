(ns cmr.dev.env.manager.process.io
  (:require
    [clojure.core.async :as async]
    [cmr.dev.env.manager.process.util :as util]
    [taoensso.timbre :as log]))

(def no-bytes -1)

(defn read-stream
  [stream bytes]
  (try
    (.read stream bytes)
    (catch Exception ex
      (log/error "Could not read stream:" (.getMessage ex))
      no-bytes)))

(defn stream->channel
  [^java.io.InputStream input-stream channel]
  (let [bytes (util/make-byte-array)]
    (async/go-loop [stream input-stream
                    bytes-read (read-stream stream bytes)]
      (when (pos? bytes-read)
        (async/>! channel (util/bytes->ascii bytes))
        (recur stream (read-stream stream bytes))))))

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
