(ns cmr.common-app.config
  "A namespace that allows for global configuration. Configuration can be provided at runtime or
  through an environment variable. Configuration items should be added using the defconfig macro."
  (:require
   [cheshire.core :as json]
   [cmr.common.config :refer [defconfig]]))

(defconfig collection-umm-version
  "Defines the latest collection umm version accepted by ingest - it's the latest official version.
   This environment variable needs to be manually set when newer UMM version becomes official"
  {:default "1.18.4"})

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
  {:default "cmr-support@nasa.gov"})

(defconfig es-unlimited-page-size
  "This is the number of items we will request from elastic search at a time when
  the page size is set to unlimited."
  {:default 10000
   :type Long})

(defconfig es-max-unlimited-hits
  "Sets an upper limit in order to get all results from elastic search
  without paging. This is used by CMR applications to load data into their
  caches."
  {:default 200000
   :type Long})

(defconfig enable-enhanced-http-logging
  "True issues an additional custom request log for all activity received from
   this server"
  {:default true
   :type Boolean})

(defconfig dump-diagnostics-on-exit-to
  "Write a HotSpotDiagnostic file to the path provided. If no path is provided,
   or the value of 'none' is given, then no file will be written."
  {:default ""})

(defconfig enable-idfed-jwt-authentication
  "Enable EDL Identity Federated JWT tokens for write operations.
   JWT tokens must meet the required-assurance-level to be accepted."
  {:default false
   :type Boolean})

(defconfig enable-launchpad-saml-authentication
  "Enable Launchpad SAML tokens for write operations."
  {:default true
   :type Boolean})

(defconfig required-assurance-level
  "Minimum assurance level required for JWT tokens (1-5).
   Level 4 (EDL+MFA) requires NON_NASA_DRAFT_USER ACL for the provider.
   Level 5 (Launchpad/PIV) bypasses the NON_NASA_DRAFT_USER ACL check."
  {:default 4
   :type Long})
