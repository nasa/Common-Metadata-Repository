(ns cmr.process.manager.core
  (:require
    [clojure.core.async :as async]
    [clojure.java.shell :as shell]
    [clojure.string :as string]
    [cmr.process.manager.util :as util]
    [taoensso.timbre :as log]
    [trifl.ps :as process]))

(def no-bytes -1)

(defn read-stream
  [stream bytes]
  (try
    (.read stream bytes)
    (catch Exception ex
      (log/warn "Could not read stream:" (.getMessage ex))
      no-bytes)))

(defn get-pid
  "Linux/Mac only!"
  [process-data]
  (process/get-pid (:process process-data)))

(defn get-descendants
  [process-data]
  (process/get-descendants (get-pid process-data)))

(defn get-cpu-mem
  [process-data]
  (process/get-ps-info "%cpu,%mem" (get-pid process-data)))

(defn get-cpu
  [process-data]
  (:%cpu (get-cpu-mem process-data)))

(defn get-mem
  [process-data]
  (:%mem (get-cpu-mem process-data)))

(defn exec
  "Executes the command and args in a sub-process, returning the result. Will
  log an error in the event the process writes to `stderr`; will log the result
  to `stdout` if the log-level is set to `:trace`."
  [& cmd-and-args]
  (log/tracef "Making sh call with %s ..." cmd-and-args)
  (let [result (apply shell/sh cmd-and-args)
        out (:out result)
        err (:err result)]
    (log/trace "Got result ...")
    (when (seq out)
      (log/trace out))
    (when (seq err)
      (log/error err))
    result))

(defn terminate-external-process!
  [pid]
  (:exit (exec "kill" "-9" (str pid))))

(defn terminate-descendants!
  [process-data]
  (doall
    (for [pid (reverse (get-descendants process-data))]
      (do
        (log/debugf "Killing child process with pid %s ..." pid)
        (let [exit-code (terminate-external-process! pid)]
          (log/debugf "Got exit code %s for child process %s." exit-code pid))))))
