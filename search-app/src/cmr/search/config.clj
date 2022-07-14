(ns cmr.search.config
  "Contains functions to retrieve search specific configuration"
  (:require
   [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig cmr-support-email
  "CMR support email address"
  {:default "cmr-support@earthdata.nasa.gov"})
