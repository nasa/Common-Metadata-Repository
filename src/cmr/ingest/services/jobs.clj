(ns cmr.ingest.services.jobs
  "This contains the scheduled jobs for the ingest application."
  (:require [cmr.common.jobs :as jobs :refer [def-stateful-job defjob]]
            [cmr.transmit.metadata-db :as mdb]))

(defn reindex-collection-permitted-groups-job
  "The implementation of the detect and reindex job"
  [context]

  ;; TODO
  ;; Get all providers from metadata db
  ;; For each provider
  ;; get the cached provider acl hash
  ;; Get the acls for the provider.
  ;; - we won't use the cache here. We'll get the latest acls
  ;; If the acl hash is different
  ;; - Send a command to the indexer to reindex all collections in a provider
  ;; -- Note the indexer will need to clear it's acl cache so that it will get the most recent acls
  ;; - Store the updated acl hash

  (let [providers (mdb/get-providers context)]
    )

  )