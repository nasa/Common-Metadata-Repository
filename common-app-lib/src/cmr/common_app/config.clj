(ns cmr.common-app.config
  "A namespace that allows for global configuration. Configuration can be provided at runtime or
  through an environment variable. Configuration items should be added using the defconfig macro."
  (:require
   [cheshire.core :as json]
   [cmr.common.config :refer [defconfig]]))

(defconfig collection-umm-version
  "Defines the latest collection umm version accepted by ingest - it's the latest official version.
   This environment variable needs to be manually set when newer UMM version becomes official"
  {:default "1.17.1"})

(defconfig launchpad-token-enforced
  "Flag for whether or not launchpad token is enforced."
  {:default false
   :type Boolean})

(defconfig release-version
  "Contains the release version of CMR."
  {:default "dev"})

(defconfig opensearch-consortiums
  "Includes all the consortiums that opensearch contains."
  {:default ["CWIC" "FEDEO" "GEOSS" "CEOS" "EOSDIS"]
   :parser #(json/decode ^String %)})

(defconfig cmr-support-email
  "CMR support email address"
  {:default "cmr-support@earthdata.nasa.gov"})

