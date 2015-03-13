(ns cmr.ingest.services.messages
  (:require [cmr.common.util :as util]
            [clojure.string :as str]))

(defn parent-collection-does-not-exist
  [provider-id granule-ur collection-ref]
  (let [{:keys [entry-title short-name version-id]} collection-ref
        field-msg (fn [field value v]
                    (if value
                      (conj v (format "%s [%s]" field value))
                      v))
        error-msg (->> []
                       (field-msg "EntryTitle" entry-title)
                       (field-msg "ShortName" short-name)
                       (field-msg "VersionID" version-id)
                       (str/join ", "))]
    (format "Collection with %s referenced in granule [%s] provider [%s] does not exist."
            error-msg granule-ur provider-id)))

(defn invalid-multipart-params
  [expected-params actual-params]
  (format "Unexpected multipart parameters: [%s]. Expected the multipart parameters [%s]."
          (str/join ", " actual-params)
          (str/join ", " expected-params)))

(defn invalid-parent-collection-for-validation
  [collection-validation-error]
  (str "The collection given for validating the granule was invalid: " collection-validation-error))