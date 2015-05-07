(ns ^{:doc "helper to provide the urls to various service endpoints"}
  cmr.system-int-test.utils.url-helper
  (:require [clojure.string :as str]
            [cmr.common.config :as config]
            [cmr.transmit.config :as transmit-config]
            [cmr.elastic-utils.config :as es-config]
            [ring.util.codec :as codec]))

(def search-public-protocol "http")
(def search-public-host "localhost")
(def search-public-port 3003)
(def search-relative-root-url "")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dev System URLs

(def dev-system-port
  "The port number for the dev system control api"
  2999)

(defn dev-system-eval-url
  "The url on the dev system control api for evaling arbitrary code in dev system"
  []
  (format "http://localhost:%s/eval" dev-system-port))

(defn dev-system-wait-for-indexing-url
  "The wait-for-indexing url on the dev system control api."
  []
  (format "http://localhost:%s/message-queue/wait-for-indexing" dev-system-port))

(defn dev-system-get-message-queue-history-url
  "The get-message-queue-history url on the dev system control api."
  []
  (format "http://localhost:%s/message-queue/history" dev-system-port))

(defn dev-system-set-message-queue-retry-behavior-url
  "The message queue set-retry-behavior url on the dev system control api."
  []
  (format "http://localhost:%s/message-queue/set-retry-behavior" dev-system-port))

(defn dev-system-set-message-queue-publish-timeout-url
  "The message queue set-message-queue-publish-timeout url on the dev system control api."
  []
  (format "http://localhost:%s/message-queue/set-publish-timeout" dev-system-port))

(defn dev-system-reset-url
  "The reset url on the dev system control api."
  []
  (format "http://localhost:%s/reset" dev-system-port))

(defn dev-system-clear-cache-url
  "The reset url on the dev system control clear cache."
  []
  (format "http://localhost:%s/clear-cache" dev-system-port))

(defn dev-system-get-component-types-url
  "The url on the dev system control api to get a map of the component types."
  []
  (format "http://localhost:%s/component-types" dev-system-port))

(defn dev-system-freeze-time-url
  []
  (format "http://localhost:%s/time-keeper/freeze-time" dev-system-port))

(defn dev-system-clear-current-time-url
  []
  (format "http://localhost:%s/time-keeper/clear-current-time" dev-system-port))

(defn dev-system-advance-time-url
  [num-secs]
  (format "http://localhost:%s/time-keeper/advance-time/%d" dev-system-port num-secs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Elasticsearch URLs

(defn elastic-root
  []
  (format "http://localhost:%s" (es-config/elastic-port)))

(defn elastic-refresh-url
  []
  (str (elastic-root) "/_refresh"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Cubby URLs

(defn cubby-health-url
  "URL to check cubby health."
  []
  (format "http://localhost:%s/health" (transmit-config/cubby-port)))

(defn cubby-reset-url
  "Resets the cubby application Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/cubby-port)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB URLs

(defn create-provider-url
  []
  (format "http://localhost:%s/providers" (transmit-config/metadata-db-port)))

(defn delete-provider-url
  [provider-id]
  (format "http://localhost:%s/providers/%s" (transmit-config/metadata-db-port) provider-id))

(defn mdb-concept-url
  "URL to concept in mdb."
  ([concept-id]
   (mdb-concept-url concept-id nil))
  ([concept-id revision-id]
   (if revision-id
     (format "http://localhost:%s/concepts/%s/%s" (transmit-config/metadata-db-port) concept-id revision-id)
     (format "http://localhost:%s/concepts/%s" (transmit-config/metadata-db-port) concept-id))))

(defn mdb-concepts-url
  "URL to concept operations in mdb."
  []
  (format "http://localhost:%s/concepts" (transmit-config/metadata-db-port)))

(defn mdb-provider-holdings-url
  "URL to retrieve provider holdings in mdb."
  []
  (format "http://localhost:%s/provider_holdings" (transmit-config/metadata-db-port)))

(defn mdb-reset-url
  "Force delete all concepts from mdb."
  []
  (format "http://localhost:%s/reset" (transmit-config/metadata-db-port)))

(defn mdb-read-caches-url
  "URL to read the mdb caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/metadata-db-port)))

(defn mdb-health-url
  "URL to check metadata db health."
  []
  (format "http://localhost:%s/health" (transmit-config/metadata-db-port)))

(defn mdb-jobs-url
  "URL to metadata db jobs api"
  []
  (format "http://localhost:%s/jobs/" (transmit-config/metadata-db-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ingest URLs

(defn reindex-collection-permitted-groups-url
  []
  (format "http://localhost:%s/jobs/reindex-collection-permitted-groups"
          (transmit-config/ingest-port)))

(defn reindex-all-collections-url
  []
  (format "http://localhost:%s/jobs/reindex-all-collections"
          (transmit-config/ingest-port)))

(defn cleanup-expired-collections-url
  []
  (format "http://localhost:%s/jobs/cleanup-expired-collections"
          (transmit-config/ingest-port)))

(defn ingest-url
  [provider-id type native-id]
  (format "http://localhost:%s/providers/%s/%ss/%s"
          (transmit-config/ingest-port)
          (codec/url-encode provider-id)
          (name type)
          (codec/url-encode native-id)))

(defn validate-url
  [provider-id type native-id]
  (format "http://localhost:%s/providers/%s/validate/%s/%s"
          (transmit-config/ingest-port)
          (codec/url-encode provider-id)
          (name type)
          (codec/url-encode native-id)))

(defn ingest-create-provider-url
  []
  (format "http://localhost:%s/providers" (transmit-config/ingest-port)))

(defn ingest-delete-provider-url
  [provider-id]
  (format "http://localhost:%s/providers/%s" (transmit-config/ingest-port) provider-id))

(defn ingest-provider-url
  [provider-id]
  ;; same as the delete URL for now
  (ingest-delete-provider-url provider-id))

(defn ingest-health-url
  "URL to check ingest health."
  []
  (format "http://localhost:%s/health" (transmit-config/ingest-port)))

(defn ingest-jobs-url
  "URL to ingest jobs api."
  []
  (format "http://localhost:%s/jobs/" (transmit-config/ingest-port)))

(defn ingest-read-caches-url
  "URL to read the ingest caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/ingest-port)))

(defn ingest-clear-cache-url
  "Clear cache in ingest app."
  []
  (format "http://localhost:%s/caches/clear-cache" (transmit-config/ingest-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search URLs

(defn search-url
  [type]
  (format "http://localhost:%s/%ss" (transmit-config/search-port) (name type)))

(defn timeline-url
  []
  (format "%s/timeline" (search-url :granule)))

(defn aql-url
  []
  (format "http://localhost:%s/concepts/search" (transmit-config/search-port)))

(defn search-reset-url
  "Clear cache in search app. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/search-port)))

(defn search-clear-cache-url
  "Clear cache in search app."
  []
  (format "http://localhost:%s/caches/clear-cache" (transmit-config/search-port)))

(defn search-read-caches-url
  "URL to read the search caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/search-port)))

(defn search-health-url
  "URL to check search health."
  []
  (format "http://localhost:%s/health" (transmit-config/search-port)))

(defn search-tile-url
  "URL to search for 2D grid tiles using input shapes"
  []
  (format "http://localhost:%s/tiles" (transmit-config/search-port)))

(defn provider-holdings-url
  "Returns the URL for retrieving provider holdings."
  []
  (format "http://localhost:%s/provider_holdings" (transmit-config/search-port)))

(defn retrieve-concept-url
  [type concept-id]
  (format "http://localhost:%s/concepts/%s" (transmit-config/search-port) concept-id))

(defn search-root
  "Returns the search url root"
  []
  (let [port (if (empty? search-relative-root-url)
               search-public-port
               (format "%s%s" search-public-port search-relative-root-url))]
    (format "%s://%s:%s/" search-public-protocol search-public-host port)))

(defn location-root
  "Returns the url root for reference location"
  []
  (str (search-root) "concepts/"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bootstrap URLs

(defn bulk-index-provider-url
  []
  (format "http://localhost:%s/bulk_index/providers" (transmit-config/bootstrap-port)))

(defn bulk-index-collection-url
  []
  (format "http://localhost:%s/bulk_index/collections" (transmit-config/bootstrap-port)))

(defn bulk-migrate-provider-url
  []
  (format "http://localhost:%s/bulk_migration/providers" (transmit-config/bootstrap-port)))

(defn db-synchronize-url
  []
  (format "http://localhost:%s/db_synchronize" (transmit-config/bootstrap-port)))

(defn bootstrap-health-url
  "URL to check bootstrap health."
  []
  (format "http://localhost:%s/health" (transmit-config/bootstrap-port)))

(defn bootstrap-jobs-url
  "URL to bootstrap jobs api."
  []
  (format "http://localhost:%s/jobs/" (transmit-config/bootstrap-port)))

(defn bootstrap-read-caches-url
  "URL to read the bootstrap caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/bootstrap-port)))

(defn bootstrap-clear-cache-url
  "Clear cache in bootstrap app."
  []
  (format "http://localhost:%s/caches/clear-cache" (transmit-config/bootstrap-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Index Set URLs

(defn index-set-reset-url
  "Delete and re-create the index set in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/index-set-port)))

(defn index-set-read-caches-url
  "URL to read the index-set caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/index-set-port)))

(defn index-set-health-url
  "URL to check index-set health."
  []
  (format "http://localhost:%s/health" (transmit-config/index-set-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indexer URLs

(defn indexer-reset-url
  "Delete and re-create indexes in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/indexer-port)))

(defn indexer-update-indexes
  "Updates the indexes in the indexer to update mappings and settings"
  []
  (format "http://localhost:%s/update-indexes" (transmit-config/indexer-port)))

(defn indexer-read-caches-url
  "URL to read the indexer caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/indexer-port)))

(defn indexer-clear-cache-url
  "Clear cache in indexer app."
  []
  (format "http://localhost:%s/caches/clear-cache" (transmit-config/indexer-port)))

(defn indexer-health-url
  "URL to check indexer health."
  []
  (format "http://localhost:%s/health" (transmit-config/indexer-port)))

