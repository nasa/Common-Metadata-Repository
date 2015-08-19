(ns cmr.indexer.data.concepts.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common-app.services.kms-fetcher :as kf]))

(defn platform-short-name->elastic-doc
  "Converts a platform into the portion going in an elastic document"
  [context short-name]
  (let [full-platform (kf/get-full-hierarchy-for-short-name context :platforms short-name)
        {:keys [category series-entity long-name]} full-platform]
    (when-not full-platform
      (info (format "Unable to find platform short-name [%s] in KMS." short-name)))
    {:category category
     :category.lowercase (when category (str/lower-case category))
     :series-entity series-entity
     :series-entity.lowercase (when series-entity (str/lower-case series-entity))
     :short-name short-name
     :short-name.lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name.lowercase (when long-name (str/lower-case long-name))}))

