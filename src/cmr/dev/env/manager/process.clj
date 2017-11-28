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
(def ^:dynamic *exit-timeout* 30000)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; type-related functions

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

(defn str->int
  [str]
  (if (= "" str)
    nil
    (Integer/parseInt str)))

;; output/error reading/logging support

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

;; process table/info support

(defn get-cmd-output
  [& cmd-args]
  (shell/stream-to-string (apply shell/proc cmd-args) :out))

(defn output-format->keys
  [output-fields]
  (->> #","
       (string/split output-fields)
       (mapv (comp keyword string/trim))))

(defn parse-output-line
  [output-format output-line]
  (case output-format
    "pid,ppid,pgid,comm"
    (let [[pid ppid pgid & cmd] (string/split output-line #"\s")]
      (conj
        (mapv str->int [pid ppid pgid])
        (string/join " " cmd)))))

(defn output-line->map
  [output-format output-line]
  (zipmap (output-format->keys output-format)
          (parse-output-line output-format output-line)))

(defn output-lines->ps-info
  [output-format output-lines]
  (map (partial output-line->map output-format) output-lines))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-ps-info
  ([]
    (get-ps-info "pid,ppid,pgid,comm"))
  ([output-format]
    (->> output-format
         (get-cmd-output "ps" "--no-headers" "-eo")
         (string/split-lines)
         (output-lines->ps-info output-format))))

(defn get-pid
  "Linux only!"
  [process-data]
  (let [process (:process process-data)
        process-field (.getDeclaredField (.getClass process) "pid")]
    (.setAccessible process-field true)
    (.getInt process-field process)))

(defn get-children
  [process-data]
  (let [parent-pid (get-pid process-data)]
    ))

(defn terminate-children!
  [process-data]
  (let [child-processes (get-children process-data)]
    ))

(defn log-process-data
  [process-data out-chan err-chan]
  (shell/flush process-data)
  (read-stream (:out process-data) out-chan)
  (log-debug out-chan)
  (read-stream (:err process-data) err-chan)
  (log-error err-chan))

(defn spawn!
  [& args]
  (let [process-data (apply shell/proc args)
        out-chan (async/chan *channel-buffer-size*)
        err-chan (async/chan *channel-buffer-size*)]
    (log-process-data process-data out-chan err-chan)
    (merge process-data {:out-channel out-chan
                         :err-channel err-chan})))

(defn terminate!
  [process-data]
  (terminate-children! process-data)
  (shell/flush process-data)
  (shell/done process-data)
  (async/close! (:out-channel process-data))
  (async/close! (:err-channel process-data))
  (shell/exit-code process-data *exit-timeout*))
