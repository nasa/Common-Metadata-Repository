(ns cmr.dev.env.manager.components.checks.health
  (:require
    [cmr.dev.env.manager.components.docker :as docker]
    [cmr.dev.env.manager.components.process :as process])
  (:import
    (cmr.dev.env.manager.components.docker DockerRunner)
    (cmr.dev.env.manager.components.process ProcessRunner)))

(defprotocol Healthful
  (get-summary [this]
    "Provides high-level view on health of a component.")
  (get-status [this]
    "Performs a health check on a given component."))

(extend DockerRunner
        Healthful
        docker/healthful-behaviour)

(extend ProcessRunner
        Healthful
        process/healthful-behaviour)
