(ns cmr.common.log
  "Defines a Logger component and functions for logging. The functions here
  should be used for logging to limit the dependency on a particular logging
  framework."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.lifecycle :as lifecycle]
   [taoensso.timbre :as timbre]
   [taoensso.timbre.appenders.core :as a-core]))

(def ^:dynamic *request-id*
  "Request id is a unique identifier to include in log messages. It's expected
  to be something like a UUID so it's easy to search for. If request id is not
  set the thread name or id will be used."
  nil)

(defn- log-formatter
  "Basic format for the log"
  [{:keys [level ?err_ msg_ timestamp_ hostname_ ?ns-str] :as _data}]
  ;; <timestamp_> <hostname_> <request id> <LEVEL> [<?ns-str>] - <msg_> <?err_>
  (format "%s %s [%s] %s [%s] - %s%s"
          (force timestamp_)
          (force hostname_)
          (or *request-id*
              (.getName (Thread/currentThread))
              (.getId (Thread/currentThread)))
          (-> level name string/upper-case)
          (or ?ns-str "?ns")
          (force msg_)
          (if-let [err (force ?err_)]
            ;; Setting :stacktrace-fonts here to an empty map prevents color
            ;; codes in exception stacktraces.
            (str "\n" (timbre/stacktrace err {:stacktrace-fonts {}}))
            "")))

;; Checkout the config before and after
(defn- setup-logging
  "Configures logging using Timbre."
  [{:keys [level file stdout-enabled?]}]

  (timbre/set-level! (or level :warn))
  (timbre/merge-config! {:timestamp-opts {:pattern "yyyy-MM-dd HH:mm:ss.SSS"}})
  (timbre/merge-config! {:min-level [[#{"org.eclipse.jetty.server.RequestLog"} :info] [#{"*"} :error]]})

  ;; Enable file logging
  (when file
    ;; Make sure the log directory exists
    (.. (io/file file) getParentFile mkdirs)
    (timbre/merge-config! {:appenders {:spit (a-core/spit-appender {:fname file})}}))

  ;; Set the format for logging.
  (timbre/merge-config!
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
  `(timbre/trace ~@body))

(defmacro debug
  "Logs a message at the debug level."
  [& body]
  `(timbre/debug ~@body))

(defmacro info
  "Logs a message at the info level."
  [& body]
  `(timbre/info ~@body))

(defmacro warn
  "Logs a message at the warn level."
  [& body]
  `(timbre/warn ~@body))

(defmacro error
  "Logs a message at the error level."
  [& body]
  `(timbre/error ~@body))

(defmacro report
  "Log a report level message. Report level logs differ from Error in that they
   always display but are not considered an Error or Fatal event and are already
   supported by the base library."
  [& body]
  `(timbre/report ~@body))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Log macros like the above but using a format like all the cool kids do

(defmacro tracef
  "Logs a message at the trace level."
  [& body]
  `(timbre/tracef ~@body))

(defmacro debugf
  "Logs a message at the debug level using a format."
  [& body]
  `(timbre/debugf ~@body))

(defmacro infof
  "Logs a message at the info level."
  [& body]
  `(timbre/infof ~@body))

(defmacro warnf
  "Logs a message at the warn level."
  [& body]
  `(timbre/warnf ~@body))

(defmacro errorf
  "Logs a message at the error level."
  [& body]
  `(timbre/errorf ~@body))

(defmacro reportf
  "Log a report level message. Report level logs differ from Error in that they
   always display but are not considered an Error or Fatal event and are already
   supported by the base library."
  [& body]
  `(timbre/reportf ~@body))

(defrecord Logger
  [level ; The level to log out
   file  ; The path to the file to log to
   stdout-enabled?]

  lifecycle/Lifecycle

  (start
    [this _system]
    (setup-logging this)
    this)

  (stop [this _system]
        this))

(def default-log-options
  "Basic log options to use if non are given."
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
                  {:level (keyword (string/lower-case level))})]
    (create-logger options)))

(defn apparent-log-level
  "Return the internal log level used by timber directly from timbre as there is no set function in
   this api to get the current version. We could query the level here, but it can change if using
   taoensso.timbre/with-log-level"
  []
  #_:clj-kondo/ignore
  (if-some [_ timbre/*config*]
    (:min-level timbre/*config*)
    :warn))
