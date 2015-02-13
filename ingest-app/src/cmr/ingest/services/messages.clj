(ns cmr.ingest.services.messages
  (:require [cmr.common.util :as util]))

(defn parent-collection-does-not-exist
  [granule-ur collection-ref]
  (if-let [entry-title (:entry-title collection-ref)]
    (format "Collection with EntryTitle [%s] referenced in granule does not exist." entry-title)
    (format "Collection with ShortName [%s] & VersionID [%s] referenced in granule does not exist."
            (:short-name collection-ref) (:version-id collection-ref))))