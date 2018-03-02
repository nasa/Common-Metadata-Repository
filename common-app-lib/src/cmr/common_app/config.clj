(ns cmr.common-app.config
  "A namespace that allows for global configuration. Configuration can be provided at runtime or
  through an environment variable. Configuration items should be added using the defconfig macro."
  (:require
   [cmr.common.config :refer [defconfig]]))

(defconfig cwic-tag
  "has-granules-or-cwic should also return any collection with configured cwic-tag"
  {:default "org.ceos.wgiss.cwic.granules.prod"})
