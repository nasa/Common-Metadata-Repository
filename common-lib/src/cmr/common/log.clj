(ns cmr.common.log
  "Defines a Logger component and functions for logging. The functions here
  should be used for logging to limit the dependency on a particular logging
  framework."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.lifecycle :as lifecycle]
   [taoensso.encore :as enc]
   [taoensso.timbre :as t]
   [taoensso.timbre.appenders.core :as a]))

(def ^:const default-logging-level
  "This is the default level to set if the starting application doesn't set it."
  :warn)

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
          (-> level name string/upper-case)
          (or ?ns-str "?ns")
          (force msg_)
          (if-let [err (force ?err_)]
            ;; Setting :stacktrace-fonts here to an empty map prevents color
            ;; codes in exception stacktraces.
            (str "\n" (t/stacktrace err {:stacktrace-fonts {}}))
            "")))

;; The following section implements run-time log changes.
(def saved-startup-logging-level
  "This definition saves the startup logging level, so that it can be reset without the
   user having to create a map to undo the changes. It is setup during setup-logging - it is also
   set here to the default just in case it isn't set in the setup-logging function below.
   Currently there is no way to delete added logging configurations - so the reset-logging
   function will serve this purpose."
  (atom default-logging-level))

(def allowed-logging-configuration-changes
  "This is the list of keys that users are allowed to change in the logging configuration"
  [:ns-blacklist :ns-whitelist :ns-pattern-map :level])

(defn get-logging-configuration
  "This function gets the logging configuration to the caller as a map."
  []
  t/*config*)

(defn get-partial-logging-configuration
  "This function returns only the logging configuration that the CMR is allowing a privileged
   user to change."
  []
  (select-keys (get-logging-configuration) allowed-logging-configuration-changes))

(def ^:const logging-levels-not-allowed
  "This is the default logging levels not allowed in a normal CMR application because it would
   harm metrics captured through logging."
  #{:error :fatal})

(defn- filter-out-main-level-not-allowed
  "This function checks to see if a user is trying to set the main logging level in the passed in
   configuration to a higher level than what is allowed. If the logging level is higher it is
   stripped out. The new configuration map is returned."
  [partial-log-config]
  (if (contains? logging-levels-not-allowed (partial-log-config :level))
    (dissoc partial-log-config :level)
    partial-log-config))

(defn- filter-out-ns-pattern-map-not-allowed
  "This function checks to see if a user is trying to set either :all or a namespace to a higher
   logging level than what is allowed. Any offending item is removed and a new configuration map
   is returned."
  [partial-log-config]
  (reduce (fn [ret [k v]]
            (if (not (contains? logging-levels-not-allowed v))
              (assoc ret k v)
              ret))
          {} (partial-log-config :ns-pattern-map)))

(defn filter-out-levels-not-allowed
  "This function makes sure that the user can't change the logging configuration beyond what is
   allowed, so that metrics can still be captured."
  [partial-log-config]
  (-> partial-log-config
      (filter-out-main-level-not-allowed)
      (assoc :ns-pattern-map (filter-out-ns-pattern-map-not-allowed partial-log-config))))

(defn merge-logging-configuration
  "This function merges the passed in configuration with the current logging configuration.
  The passed in configuration map includes a map of name spaces with logging levels and at the end
  use :all and its level to signify what all the other non identified name spaces' logging level
  should be.
  An example of a passed in configuration is as follows
  {:level :info
   :ns-pattern-map {\"namespace1.foo.bar\" :warn
                    \"namespace2.another.level.down\" :debug
                    :all :info}})
  This function returns the newly merged logging configuration."
  [partial-log-config]
  (t/merge-config! partial-log-config))

(defn merge-partial-logging-configuration
  "This function strips out from the pass in log configuration all of the allowable keys and values
   and creates a new map from them to be merged into the CMR timbre configuration. It also then
   strips out any item that tries to set logging higher than what is allowed. This function
   returns the partial new configuration."
  [partial-log-config]
  (-> (select-keys partial-log-config allowed-logging-configuration-changes)
      (filter-out-levels-not-allowed)
      (merge-logging-configuration)
      (select-keys allowed-logging-configuration-changes)))

(defn reset-logging-configuration
  "This function resets the config as it was in the beginning and returns the new
   partial configuration"
  []
  (t/set-config!
   (assoc t/*config* :level @saved-startup-logging-level
                     :ns-whitelist []
                     :ns-blacklist []
                     :ns-pattern-map {:all @saved-startup-logging-level}))
  (get-partial-logging-configuration))

;; The next three functions are needed to allow run time logging. The base
;; knowledge for these functions came from here:
;; https://github.com/ptaoussanis/timbre/issues/208
;; https://github.com/yonatane/timbre-ns-pattern-level
;; I modified the above solutions to be able to set a log level by namespace
;; during run time.

(defn ns-filter
  "Returns a memoized version of a referentially transparent function. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. The transparent function
  is the passed in filter which returns (fn [?ns]) -> truthy. Some example patterns:)
  \"foo.bar\", \"foo.bar.*\", #{\"foo\" \"bar\"},
  {:whitelist [\"foo.bar.*\"] :blacklist [\"baz.*\"]
  https://ptaoussanis.github.io/tufte/taoensso.tufte.html#var-compile-ns-filter"
  [fltr]
  (-> fltr
      enc/compile-ns-filter
      enc/memoize_))

(defn get-namespace-from-pattern-map
  "This function either finds the passed in ?ns-str (which is a string representation of a
   namespace such as \"cmr.commom.log\") in the passed in ns-pattern-map which looks like the
   following:
   For example :ns-pattern-map {\"namespace1.foo.bar\" :warn
                                \"namespace2.another.level.down\" :debug
                                :all :info}
   or it passes back the :all keyword."
  [ns-pattern-map ?ns-str]
  (or (some->> ns-pattern-map
               keys
               (filter #(and (string? %)
                             ((ns-filter %) ?ns-str)))
               not-empty
               (apply max-key count))
     :all))

(defn log-by-ns-pattern
  "This is a logging middleware function. It takes all logging messages
   passed to it by timbre. This function pulls out three parameters by key
   1) the log message's namespace represented as a string
   2) the current timbre configuration file
   3) The log message's logging level
   This function pulls out the current ns-pattern-map that is defined in the current timbre
   configuration by a key of the same name. It uses the map to pull out the string representation
   of the namespace of the passed in log message and its defined logging level if the namespace
   string exists. If it doesn't then it pulls out the :all keyword with its defined logging level.
   The namespace or :all key is stored in the namesp variable and it's defined logging level is
   stored in the loglevel variable. Just in case the namespace can't be found and the :all key is
   not present the loglevel will be set to the top level timbre configuration logging level :level.
   This ns-pattern-map is in the form of namespace level and for all other names spaces not
   explicitly listed, the logging level is defined by the :all key.
   For example :ns-pattern-map {\"namespace1.foo.bar\" :warn
                                \"namespace2.another.level.down\" :debug
                                :all :info}
   With this information this function filters out log messages. First it checks to see if the
   the log message's logging level is equal to or more severe (towards error) to the main log level
   (:level at the top of the timbre configuration). If it is not then it is filtered out. It then
   checks to see if the log message's logging level is at the level or more severe then the logging
   level defined by the namespace or the :all key in the ns-pattern-map.

   Here is a less verbose description for those that may understand it:
   What it does precisely is: given the data structure to be logged (as the 1st arg) determines
   whether the logging will actually happen or not by looking up in the configuration hashmap
   ns-pattern-map, which has either the namespace string filters as specified in
   taoensso.encore/ns-filter - or the keyword :all- as keys, and the level as a value (es. :debug)"
  [& [{:keys [?ns-str config level] :as opts}]]
  (let [ns-pattern-map (get config :ns-pattern-map)
        namesp (get-namespace-from-pattern-map ns-pattern-map ?ns-str)
        loglevel (get ns-pattern-map namesp (get config :level))]  ;(get map key not-found)
    ;; checks to see if the namespace message log level (loglevel) is equal or more severe
    ;; (towards error) to the main log level (:level at the top of the timbre configuration)
    ;; and the second check, checks to see if the message level is at the level of the
    ;; namespace or more severe
    (when (and (t/may-log? loglevel namesp)
               (t/level>= level loglevel))
      opts)))

;; *** the runtime logging changes section end.

;; Checkout the config before and after
(defn- setup-logging
  "Configures logging using Timbre."
  [{:keys [level file stdout-enabled?]}]
  (let [level-to-set (or level default-logging-level)]

    ;; Set the saved-startup-logging-level so that it can be recalled
    ;; if the user wants to reset the logging.
    (reset! saved-startup-logging-level level-to-set)

    (t/set-level! level-to-set)
    (t/merge-config! {:timestamp-opts {:pattern "yyyy-MM-dd HH:mm:ss.SSS"}})

    ;; Enable file logging
    (when file
      ;; Make sure the log directory exists
      (.. (io/file file) getParentFile mkdirs)
      (t/merge-config! {:appenders {:spit (a/spit-appender {:fname file})}}))

    ;; Set the format for logging.
    (t/merge-config!
      {:output-fn log-formatter
       :appenders {:println {:enabled? stdout-enabled?}}})

    ;; this is to be able to set a log level by namespace at run time
    (t/merge-config! {:middleware [(partial log-by-ns-pattern)]})
    (t/merge-config! {:ns-pattern-map {:all level-to-set}})))

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
                  {:level (keyword (string/lower-case level))})]
    (create-logger options)))
