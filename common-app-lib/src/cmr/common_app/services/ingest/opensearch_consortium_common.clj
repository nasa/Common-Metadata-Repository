(ns cmr.common-app.services.ingest.opensearch-consortium-common
  "This contains the opensearch consortiums to be shared
   between search and ingest."
  (:require
   [clojure.string :as str]
   [cmr.common-app.config :as common-config]))

(def opensearch-consortium-list
  "Defines a list that contains all the opensearch consortiums."
  (remove empty? (str/split (str/upper-case (common-config/opensearch-consortiums)) #" ")))
