(ns cmr.common.log
  "Defines a Logger component and functions for logging. The functions here
  should be used for logging to limit the dependency on a particular logging
  framework."
  (:require [cmr.common.lifecycle :as lifecycle]
            [taoensso.timbre :as t]
            [taoensso.timbre.appenders.core :as a]
            [clojure.string :as s]
            [clojure.java.io :as io]))

(def ^:dynamic *request-id*
  "Request id is a unique identifier to include in log messages. It's expected
  to be something like a UUID so it's easy to search for. If request id is not
  set the thread name or id will be used."
  nil)

(defn- log-formatter
  [{:keys [level ?err_ msg_ timestamp_ hostname_ ?ns-str] :as data}]
  ;; <timestamp_> <hostname_> <request id> <LEVEL> [<?ns-str>] - <msg_> <?err_>
  (format "%s %s [%s] %s [%s] - %s%s"
          (force timestamp_)
          (force hostname_)
          (or *request-id*
              (.getName (Thread/currentThread))
              (.getId (Thread/currentThread)))
          (-> level name s/upper-case)
          (or ?ns-str "?ns")
          (force msg_)
          (if-let [err (force ?err_)]
            ;; Setting :stacktrace-fonts here to an empty map prevents color
            ;; codes in exception stacktraces.
            (str "\n" (t/stacktrace err {:stacktrace-fonts {}}))
            "")))

;; Checkout the config before and after
(defn- setup-logging
  "Configures logging using Timbre."
  [{:keys [level file stdout-enabled?]}]

  (t/set-level! (or level :warn))
  (t/merge-config! {:timestamp-opts {:pattern "yyyy-MM-dd HH:mm:ss.SSS"}})

  ;; Enable file logging
  (when file
    ;; Make sure the log directory exists
    (.. (io/file file) getParentFile mkdirs)
    (t/merge-config! {:appenders {:spit (a/spit-appender {:fname file})}}))

  ;; Set the format for logging.
  (t/merge-config!
    {:output-fn log-formatter
     :appenders {:println {:enabled? stdout-enabled?}}}))

(defmacro with-request-id
  "Sets the dynamic var *request-id* so that any log messages executed within
  this binding will include the given request id."
  [request-id & body]
  `(binding [*request-id* ~request-id]
     ~@body))

;; Macros for logging. Macros are used because the timbre calls are macros. I
;; think they're more efficient if they are macros when there is a lot of
;; logging.

(defmacro trace
  "Logs a message at the trace level."
  [& body]
  `(t/trace ~@body))

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
  [level ; The level to log out
   file  ; The path to the file to log to
   stdout-enabled?]

  lifecycle/Lifecycle

  (start
    [this system]
    (setup-logging this)
    this)

  (stop [this system]
        this))

(def default-log-options
  {:level :info
   :file nil ; Do not log to a file by default
   :stdout-enabled? true})

(defn create-logger
  "Creates a new logger. Can accept a map of options or no arguments for
  defaults."
  ([]
   (create-logger default-log-options))
  ([options]
   (map->Logger (merge default-log-options options))))

(defn create-logger-with-log-level
  "Creates a new logger with a logging level set to the given level.
   level argument is a string with possible values of error, warn, info and
   debug."
  [level]
  (let [options (when level
                  {:level (keyword (s/lower-case level))})]
    (create-logger options)))
