(ns cmr.indexer.data.concepts.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common-app.services.kms-fetcher :as kf]))

(def FIELD_NOT_PRESENT
  "A string to indicate that a field is not present within a KMS keyword."
  "UNKNOWN")

(defn platform-short-name->elastic-doc
  "Converts a platform into the portion going in an elastic document. If a field is not present in
  the KMS hierarchy we use a dummy value to indicate the field was not present."
  [gcmd-keywords-map short-name]
  (let [full-platform (kf/get-full-hierarchy-for-short-name gcmd-keywords-map :platforms short-name)
        {:keys [category series-entity long-name uuid]} full-platform]
    {:category (or category FIELD_NOT_PRESENT)
     :category.lowercase (str/lower-case (or category FIELD_NOT_PRESENT))
     :series-entity (or series-entity FIELD_NOT_PRESENT)
     :series-entity.lowercase (str/lower-case (or series-entity FIELD_NOT_PRESENT))
     :short-name short-name
     :short-name.lowercase (str/lower-case short-name)
     :long-name (or long-name FIELD_NOT_PRESENT)
     :long-name.lowercase (str/lower-case (or long-name FIELD_NOT_PRESENT))
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))
