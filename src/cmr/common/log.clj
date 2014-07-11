(ns cmr.common.log
  "Defines a Logger component and functions for logging. The functions here should be used for logging
  to limit the dependency on a particular logging framework."
  (:require [cmr.common.lifecycle :as lifecycle]
            [taoensso.timbre :as t]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(defn- setup-logging
  "Configures logging using Timbre."
  [{:keys [level file stdout-enabled?]}]

  (t/set-level! (or level :warn))
  (t/set-config! [:timestamp-pattern] "yyyy-MM-dd HH:mm:ss.SSS")

  (if file
    ;; Enable file logging
    (do
      ;; Make sure the log directory exists
      (.. (io/file file) getParentFile mkdirs)
      (t/set-config! [:appenders :spit :enabled?] true)
      (t/set-config! [:shared-appender-config :spit-filename] file))
    ;; Disable file logging
    (t/set-config! [:appenders :spit :enabled?] false))


  ;; Set the format for logging.
  (t/set-config! [:fmt-output-fn]
                 (fn [{:keys [level throwable message timestamp hostname ns]}
                      ;; Any extra appender-specific opts:
                      & [{:keys [nofonts?] :as appender-fmt-output-opts}]]
                   ;; <timestamp> <hostname> <thread id> <LEVEL> [<ns>] - <message> <throwable>
                   (format "%s %s %s %s [%s] - %s%s"
                           timestamp
                           hostname
                           (.getId (Thread/currentThread))
                           (-> level name s/upper-case)
                           ns
                           (or message "")
                           (or (t/stacktrace throwable "\n" (when nofonts? {})) ""))))


  ; Enable/disable stdout logs
  (t/set-config! [:appenders :standard-out :enabled?] stdout-enabled?))

;; Macros for logging. Macros are used because the timbre calls are macros.
;; I think they're more efficient if they are macros when there is a lot of logging.

(defmacro debug
  "Logs a message at the debug level."
  [& body]
  `(t/debug ~@body))

(defmacro info
  "Logs a message at the info level."
  [& body]
  `(t/info ~@body))

(defmacro warn
  "Logs a message at the warn level."
  [& body]
  `(t/warn ~@body))

(defmacro error
  "Logs a message at the error level."
  [& body]
  `(t/error ~@body))

(defrecord Logger
  [
   ; The level to log out
   level
   ;; The path to the file to log to
   file
   ;;
   stdout-enabled?
   ]

  lifecycle/Lifecycle

  (start
    [this system]
    (setup-logging this)
    this)

  (stop [this system]
        this))

(def default-log-options
  {:level :debug
   ;; Do not log to a file by default
   :file nil
   :stdout-enabled? true})

(defn create-logger
  "Creates a new logger. Can accept a map of options or no arguments for defaults."
  ([]
   (create-logger default-log-options))
  ([options]
   (map->Logger (merge default-log-options options))))
