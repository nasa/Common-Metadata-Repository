(ns cmr.ingest.services.messages
  (:require [cmr.common.util :as util]
            [clojure.string :as str]))

(defn parent-collection-does-not-exist
  [granule-ur collection-ref]
  (if-let [entry-title (:entry-title collection-ref)]
    (format "Collection with EntryTitle [%s] referenced in granule does not exist." entry-title)
    (format "Collection with ShortName [%s] & VersionID [%s] referenced in granule does not exist."
            (:short-name collection-ref) (:version-id collection-ref))))

(defn invalid-multipart-params
  [expected-params actual-params]
  (format "Unexpected multipart parameters: [%s]. Expected the multipart parameters [%s]."
          (str/join ", " actual-params)
          (str/join ", " expected-params)))

(defn invalid-parent-collection-for-validation
  [collection-validation-error]
  (str "The collection given for validating the granule was invalid: " collection-validation-error))