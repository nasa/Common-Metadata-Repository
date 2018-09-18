(ns cmr.process.manager.util
  (:require
    [clojure.java.io :as io]
    [cmr.exchange.common.util :as util]
    [me.raynes.conch.low-level :as shell]))

(def ^:dynamic *byte-buffer-size* 1024)

;; XXX Copied here for backwards compatibility; can be removed when references
;; are updated.
(def newline? util/newline?)
(def bytes->ascii util/bytes->ascii)
(def bytes->utf8 util/bytes->utf8)
(def bytes->int util/bytes->int)
(def str->bytes util/str->bytes)
(def str->stream util/str->stream)

(defn make-byte-array
  ([]
    (make-byte-array *byte-buffer-size*))
  ([buffer-size]
    (make-array Byte/TYPE buffer-size)))

(defn get-cmd-output
  [& cmd-args]
  (shell/stream-to-string (apply shell/proc cmd-args) :out))
