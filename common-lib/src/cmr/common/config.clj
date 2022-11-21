(ns cmr.common.config
  "A namespace that allows for global configuration. Configuration can be provided at runtime or
  through an environment variable. Configuration items should be added using the defconfig macro."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.log :as log :refer [debug info warn error]]
   [environ.core :as environ]))

(defonce ^{:private true
           :doc "An atom containing a map of explicitly set config values."
           ;; dynamic is here only for testing purposes
           :dynamic true}
  runtime-config-values
  (atom {}))

(defn set-config-value!
  "Sets a config value at runtime. This allows overriding values at runtime without having to
  specify it as a env variable. Prefer using the generated setter methods from defconfig over
  this method of setting values."
  [name value]
  (swap! runtime-config-values #(assoc % name value))
  nil)

(defn reset-config-values
  "Resets any explicitly set runtime configuration values."
  []
  (reset! runtime-config-values {}))

(defn config-name->env-name
  "Converts a config name into the environment variable name"
  [config-name]
  (str "CMR_" (csk/->SCREAMING_SNAKE_CASE_STRING config-name)))

;; dynamic is here only for testing purposes
(defn- ^:dynamic env-var-value
  "Returns the value of the environment variable. Here specifically to enable testing of this
  namespace."
  [env-name]
  (environ/env (csk/->kebab-case-keyword env-name)))

(defn config-value*
  "Retrieves the currently configured value for the name or the default value. A parser function
  must be specified for parsing the value out of the environment variable. Do not call this function
  directly. Use defconfig instead."
  [config-name default-value parser-fn]
  (let [parser-fn (or parser-fn identity)]
    (let [override-value (get @runtime-config-values config-name)
          parsed-env-value (some-> (env-var-value (config-name->env-name config-name))
                               parser-fn)]
      (cond
        (some? parsed-env-value) parsed-env-value
        (some? override-value) override-value
        :else default-value))))

(defonce ^{:doc "This contains information about all of the
                 configuration parameters that have been added using
                 defconfig. It is used for allowing documentation
                 generation of configuration parameters. It's a map of
                 namespaces to configuration keys to options maps."}
  configs-atom
  (atom {}))

(defn register-config
  "Registers a configuration parameter in the configs atom."
  [config-namespace config-key doc-string options]
  (swap! configs-atom
         assoc-in [config-namespace config-key]
         (assoc options :doc-string doc-string)))

(defn print-all-configs-docs
  "Prints out documentation on all known configuration parameters"
  []
  (println
    (str
      "Configuration Documentation\n"
      (str/join
        "\n"
        ;; Print out the documentation in namespace order
        (for [[config-namespace sub-map] (sort-by first @configs-atom)]
          (str
            "\n-- " config-namespace " --\n"
            (str/join
              "\n"
              ;; Within a namespace print it out in config key order
              (for [[config-key {:keys [default doc-string parser] config-type :type}] (sort-by first sub-map)
                    :let [env-name (config-name->env-name config-key)
                          current (config-value* config-key default parser)
                          type-name (if (class? config-type)
                                      (.getSimpleName ^Class config-type)
                                      (str config-type))]]
                (str "\n" env-name "\n" doc-string
                     "\nType: " type-name
                     "\nDefault: " (pr-str default)
                     "\nCurrent: " (pr-str current))))))))))

(comment
  (print-all-configs-docs))



(defn parse-boolean
  "Helper for parsing boolean strings."
  [s]
  (cond
    (or (= s true) (= s false)) s
    (= s "true") true
    (= s "false") false
    :else (throw (Exception. (str "Unexpected value [" (pr-str s) "] for parsing a boolean.")))))

(defn maybe-long
  "Returns a Long parsed from s when s is not nil, otherwise nil."
  [s]
  (when s
    (Long. ^String s)))

(def type->parser
  "Maps config types to value parser functions"
  {String identity
   Long #(Long. ^String %)
   Double #(Double. ^String %)
   Boolean parse-boolean
   :edn edn/read-string})

(defmacro defconfig
  "Defines a configuration parameter that will be taken from environment variable of the form
  UPPER_SNAKE_CASE with the prefix CMR_. The doc string and options will be available in the
  generated documentation for configuration parameters. A function of the same name as the config
  will be generated for retrieving values. In addition a function of the name 'set-<name>!' will be
  generated to allow setting an override value.

  Options:

  * :default - Sets the default value if none is provided. Required.
  * :type - Sets the type of value the configuration parameter will have. The type will be used
  to determine how to parse the environment variable value.
  * :parser - A custom parser function to use to parse the environment variable value.

  If not type or parser is provided the type defaults to String.

  Example:

  (defconfig listen-port
  \"The port the application should use for requests.\"
  {:default 3000
  :type Long})

  The value will be taken from an environment variable with the name CMR_LISTEN_PORT.

  Functions available after that config is used:
  * (listen-port) - returns the currently configured value of the configuration value.
  * (set-listen-port! new-value) - Sets an override value."
  [config-name-symbol doc-string options]

  (let [{default :default config-type :type parser :parser} options
        config-type (if config-type
                      (if (= :edn config-type)
                        config-type
                        (resolve config-type))
                      String)]
    (when-not doc-string
      (throw (Exception. "defconfig :doc-string is required")))
    (when (and (nil? parser) config-type (nil? (type->parser config-type)))
      (throw (Exception. (str "Unrecognized defconfig type: " config-type))))

    (let [config-name (name config-name-symbol)
          config-name-key (keyword config-name)
          getter-name config-name-symbol
          getter-doc (format "Retrieves the configuration value of %s. %s" config-name doc-string)
          setter-name (symbol (str "set-" config-name "!"))
          setter-doc (format "Sets an override value of configuration param %s. %s"
                             config-name doc-string)
          parser-fn (or parser (type->parser config-type))]

      `(let [default-value# ~default
             doc-string-value# ~doc-string]

         ;; Check that the type of the default value matches the type specified
         ;; This has to be done here to allow for the default value to be the result of calling a function
         (when (and (nil? ~parser)
                    (not= :edn ~config-type)
                    (some? default-value#)
                    (not= (type default-value#) ~config-type))
           (throw
             (Exception.
               (format "The type of the default value %s does not match the specified config type %s"
                       (type default-value#) ~config-type))))

         ;; Register the config
         (register-config ~(str *ns*) ~config-name-key doc-string-value#
                          ~(assoc options
                                  ;; Assoc in type to show default of string in docs.
                                  :type config-type
                                  ;; Assoc in parser so that the parser will be used when printing values.
                                  :parser parser-fn))

         ;; Create the getter
         (defn ~getter-name
           ~getter-doc
           []
           (config-value* ~config-name-key default-value# ~parser-fn))

         ;; Create the setter
         (defn ~setter-name
           ~setter-doc
           [value#]
           (set-config-value! ~config-name-key value#))))))

(defconfig defn-timed-debug
  "This sets the switch for the logging of the debug info from defn-timed macro.
   If it is set to true, the debug info will be logged."
  {:default false
   :type Boolean})

(defconfig approved-pipeline-documents
  "This is the feature toggle for the new document pipeline prototype, as well as serving as
   the base truth list of approved document types. For tests to process, grid is required,
   but grid is not needed in any deployment envirnment. AWS should use JSON as those values
   are passed through the parser and returned as if it was the default."
  {:default {:grid ["0.0.1"]
             :data-quality-summary ["1.0.0"]
             :order-option ["1.0.0"]
             :service-entry ["1.0.0"]
             :service-option ["1.0.0"]}
   :parser #(json/parse-string % true)})

(defconfig approved-pipeline-documentation
  "This is the feature toggle for activating documentation for generic types
   it's default state is the same value as the approved-pipeline-documents but,
   can be managaged seperately"
  {:default (approved-pipeline-documents)
   :parser #(json/parse-string % true)})

(defconfig generic-ingest-disabled-list
  "This is a toggle to prevent specified generic concepts from being ingested into CMR,
   Should be an array of keys with the name of the schema to be blocked from ingest.
   Example  \"grid,order-option\", would prevent grids and order options from being ingested into CMR
   if no concepts should be blocked from ingest set to an empty array"
  {:default []
   :parser #(map (comp keyword str/trim) (str/split % #","))})

(defn check-env-vars
  "Checks any environment variables starting with CMR_ are recognized as known environment variables.
  If any are unrecognized a warning message is logged. Usually this should be called at the start
  of an application when it first starts up. "
  ([]
   (check-env-vars environ/env))
  ([env-var-map]
   (let [known-env-vars (for [[_ sub-map] @configs-atom
                              [config-key _] sub-map]
                          (config-name->env-name config-key))
         cmr-env-vars (map csk/->SCREAMING_SNAKE_CASE_STRING
                           (filter #(.startsWith (name %) "cmr-")
                                   (keys env-var-map)))
         unknown-vars (set/difference (set cmr-env-vars) (set known-env-vars))]
     (if (seq unknown-vars)
       (do
         (warn "POTENTIAL CONFIGURATION ERROR: The following CMR Environment variables were configured but were not recognized:"
               (pr-str unknown-vars))
         true)
       false))))
