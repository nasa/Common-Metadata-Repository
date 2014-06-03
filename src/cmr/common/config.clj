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

(defn config-value-fn
  "Returns a function that can retrieve a configuration value which can be set as an environment
  variable on the command line or default to a value."
  [config-name default-value]

  ;; Environment variables can't change at runtime so we look them up initially.
  (let [env-name (str "CMR_" (csk/->SNAKE_CASE_STRING config-name))
        env-value (System/getenv env-name)]
    (fn []
      (or (get @runtime-config-values config-name)
          env-value
          default-value))))