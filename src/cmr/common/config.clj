(ns cmr.common.config
  "A namespace that allows for global configuration. Configuration can be provided at runtime or
  through an environment variable"
  (:require [clojure.string :as str]
            [camel-snake-kebab :as csk]))

(def runtime-config-values
  "An atom containing a map of explicitly set config values."
  (atom {}))

(defn set-config-value!
  "Sets a config value at runtime. This allows something like dev-system
  to change a port at runtime without having to specify it as a env variable"
  [name value]
  (swap! runtime-config-values #(assoc % name value)))

(defn reset-config-values
  "Resets any explicitly set runtime configuration values."
  []
  (reset! runtime-config-values {}))

(defn config-name->env-name
  "Converts a config name into the environment variable name"
  [config-name]
  (str "CMR_" (csk/->SNAKE_CASE_STRING config-name)))

(defn config-value
  "Retrieves the currently configured value for the name or the default value.
  A parser function can optionally be specified for parsing the value out of the environment variable
  which comes back as a string unless the parser function is provided."
  ([config-name default-value]
   (config-value config-name default-value identity))
  ([config-name default-value parser-fn]
   (let [value (or (get @runtime-config-values config-name)
                   (System/getenv (config-name->env-name config-name))
                   default-value)]
     (when value
       (parser-fn value)))))

(defn config-value-fn
  "Returns a function that can retrieve a configuration value which can be set as an environment
  variable on the command line or default to a value.
  A parser function can optionally be specified for parsing the value out of the environment variable
  which comes back as a string unless the parser function is provided."
  ([config-name default-value]
   (config-value-fn config-name default-value identity))
  ([config-name default-value parser-fn]

   ;; Environment variables can't change at runtime so we look them up initially.
   (let [parser-when #(when % (parser-fn %))
         parsed-default (parser-when default-value)
         env-value (parser-when (System/getenv (config-name->env-name config-name)))]
     (fn []
       (or (parser-when (get @runtime-config-values config-name))
           env-value
           parsed-default)))))
