(ns cmr.dev.env.manager.process.core
  (:require
    [clojure.core.async :as async]
    [clojure.java.shell :as clj-shell]
    [clojure.string :as string]
    [cmr.dev.env.manager.process.const :as const]
    [cmr.dev.env.manager.process.info :as info]
    [cmr.dev.env.manager.process.io :as io]
    [cmr.dev.env.manager.process.util :as util]
    [me.raynes.conch.low-level :as shell]
    [taoensso.timbre :as log]))

(defn get-ps-info
  ([]
    (get-ps-info "pid,ppid,pgid,comm"))
  ([output-format]
    (->> output-format
         (util/get-cmd-output "ps" "--no-headers" "-eo")
         (string/split-lines)
         (info/output-lines->ps-info output-format))))

(defn get-pid
  "Linux only!"
  [process-data]
  (let [process (:process process-data)
        process-field (.getDeclaredField (.getClass process) "pid")]
    (.setAccessible process-field true)
    (.getInt process-field process)))

(defn get-children
  [process-data]
  (let [ps-info (get-ps-info)
        parent-pid (get-pid process-data)]
    (flatten (info/get-children ps-info [] [parent-pid]))))

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

(defn terminate-children!
  [process-data]
  (doall
    (for [pid (reverse (get-children process-data))]
      (do
        (log/debugf "Killing child process with pid %s ..." pid)
        (let [exit-code (terminate-external-process! pid)]
          (log/debugf "Got exit code %s for child process %s." exit-code pid))))))

(defn log-process-data
  [process-data out-chan err-chan]
  (shell/flush process-data)
  (io/stream->channel (:out process-data) out-chan)
  (io/log-debug out-chan)
  (io/stream->channel (:err process-data) err-chan)
  (io/log-error err-chan))

(defn spawn!
  [& args]
  (let [process-data (apply shell/proc args)
        out-chan (async/chan const/*channel-buffer-size*)
        err-chan (async/chan const/*channel-buffer-size*)]
    (log-process-data process-data out-chan err-chan)
    (merge process-data {:out-channel out-chan
                         :err-channel err-chan})))

(defn terminate!
  [process-data]
  (shell/flush process-data)
  (shell/done process-data)
  (terminate-children! process-data)
  (async/close! (:out-channel process-data))
  (async/close! (:err-channel process-data))
  (let [exit (future (shell/exit-code process-data))]
    (shell/destroy process-data)
    @exit))
