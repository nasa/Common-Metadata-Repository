(ns cmr.common.config
  "A namespace that allows for global configuration. Configuration can be provided at runtime or
  through an environment variable. Configuration items should be added using the defconfig macro."
  (:require [clojure.string :as str]
            [camel-snake-kebab :as csk]
            [clojure.set :as set]
            [cmr.common.log :as log :refer (debug info warn error)]))

(def ^:private runtime-config-values
  "An atom containing a map of explicitly set config values."
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
  (str "CMR_" (csk/->SNAKE_CASE_STRING config-name)))

(defn- env-var-value
  "Returns the value of the environment variable. Here specifically to enable testing of this
  namespace."
  [env-name]
  (System/getenv env-name))

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

(defn ^:deprecated config-value-fn
  "DEPRECATED: This will be removed eventually. Use defconfig instead.

  Returns a function that can retrieve a configuration value which can be set as an environment
  variable on the command line or default to a value.
  A parser function can optionally be specified for parsing the value out of the environment variable
  which comes back as a string unless the parser function is provided."
  ([config-name default-value]
   (config-value-fn config-name default-value identity))
  ([config-name default-value parser-fn]

   ;; Environment variables can't change at runtime so we look them up initially.
   (let [parser-when #(when (some? %) (parser-fn %))
         parsed-default (parser-when default-value)
         env-value (parser-when (env-var-value (config-name->env-name config-name)))]
     (fn []
       (or (parser-when (get @runtime-config-values config-name))
           env-value
           parsed-default)))))

(def configs-atom
  "This contains information about all of the configuration parameters that have been added using
  defconfig. It is used for allowing documentation generation of configuration parameters. It's a map
  of namespaces to configuration keys to options maps."
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
        (for [[config-namespace sub-map] @configs-atom]
          (str
            "\n-- " config-namespace " --\n"
            (str/join
              "\n"
              (for [[config-key {:keys [default doc-string parser] config-type :type}] sub-map
                    :let [env-name (config-name->env-name config-key)
                          current (config-value* config-key default parser)]]
                (str "\n" env-name "\n" doc-string
                     "\nType: " (.getSimpleName ^Class config-type)
                     "\nDefault: " (pr-str default)
                     "\nCurrent: " (pr-str current))))))))))

(comment
  (print-all-configs-docs)

  )

(defn parse-boolean
  "Helper for parsing boolean strings."
  [s]
  (cond
    (or (= s true) (= s false)) s
    (= s "true") true
    (= s "false") false
    :else (throw (Exception. (str "Unexpected value [" (pr-str s) "] for parsing a boolean.")))))

(def type->parser
  "Maps config types to value parser functions"
  {String identity
   Long #(Long. %)
   Double #(Double. %)
   Boolean parse-boolean})

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
                      (resolve config-type)
                      String)]
    (when-not doc-string
      (throw (Exception. "defconfig doc-string is required")))
    (when-not default
      (throw (Exception. "defconfig default is required")))
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

;; TODO put this in place once we get rid of config-value-fn and have everything using defconfig
(defn check-env-vars
  "Checks any environment variables starting with CMR_ are recognized as known environment variables.
  If any are unrecognized a warning message is logged. Usually this should be called at the start
  of an application when it first starts up. "
  ([]
   (check-env-vars (System/getenv)))
  ([env-var-map]
   (let [known-env-vars (for [[_ sub-map] @configs-atom
                              [config-key _] sub-map]
                          (config-name->env-name config-key))
         cmr-env-vars (filter #(.startsWith ^String % "CMR_")
                              (keys env-var-map))
         unknown-vars (set/difference (set cmr-env-vars) (set known-env-vars))]
     (when (seq unknown-vars)
       (warn "POTENTIAL CONFIGURATION ERROR: The following CMR Environment variables were configured but were not recognized:"
             (pr-str unknown-vars))))))

