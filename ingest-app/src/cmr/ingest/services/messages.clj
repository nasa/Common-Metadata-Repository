(ns cmr.ingest.services.messages
  (:require [cmr.common.util :as util]))

(defn parent-collection-does-not-exist
  [granule-ur collection-ref]
  (format
    "Parent collection for granule [%s] does not exist. Collection reference: %s"
    granule-ur
    (pr-str (util/remove-nil-keys (into {} collection-ref)))))