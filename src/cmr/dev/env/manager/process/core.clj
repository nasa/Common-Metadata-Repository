(ns cmr.dev.env.manager.process.core
  (:require
    [clojure.core.async :as async]
    [clojure.java.shell :as clj-shell]
    [clojure.string :as string]
    [cmr.dev.env.manager.process.const :as const]
    [cmr.dev.env.manager.process.util :as util]
    [me.raynes.conch.low-level :as shell]
    [taoensso.timbre :as log]
    [trifl.ps :as process]))

(def no-bytes -1)

(defn read-stream
  [stream bytes]
  (try
    (.read stream bytes)
    (catch Exception ex
      (log/error "Could not read stream:" (.getMessage ex))
      no-bytes)))

(defn get-pid
  "Linux/Mac only!"
  [process-data]
  (process/get-pid (:process process-data)))

(defn get-descendants
  [process-data]
  (process/get-descendants (get-pid process-data)))

(defn terminate-external-process!
  [pid]
  (let [result (clj-shell/sh "kill" "-9" (str pid))
        out (:out result)
        err (:err result)]
    (when (seq out)
      (log/debug out))
    (when (seq err)
      (log/error err))
    (:exit result)))

(defn terminate-descendants!
  [process-data]
  (doall
    (for [pid (reverse (get-descendants process-data))]
      (do
        (log/debugf "Killing child process with pid %s ..." pid)
        (let [exit-code (terminate-external-process! pid)]
          (log/debugf "Got exit code %s for child process %s." exit-code pid))))))
