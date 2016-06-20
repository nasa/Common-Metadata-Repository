(ns cmr.system-int-test.utils.url-helper
  "helper to provide the urls to various service endpoints"
  (:require [clojure.string :as str]
            [cmr.common.config :as config]
            [cmr.transmit.config :as transmit-config]
            [cmr.elastic-utils.config :as es-config]
            [ring.util.codec :as codec])
  (:import java.net.URL))

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

(defn elastic-delete-tags-url
  []
  (str (elastic-root) "/1_tags/_query"))

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

(defn mdb-force-delete-concept-url
  [concept-id revision-id]
  (format "http://localhost:%s/concepts/force-delete/%s/%s"
          (transmit-config/metadata-db-port)
          concept-id revision-id))

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

(defn mdb-old-revision-cleanup-job-url
  "URL to metadata db old revision cleanup job"
  []
  (format "http://localhost:%s/jobs/old-revision-concept-cleanup" (transmit-config/metadata-db-port)))

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

(defn translate-metadata-url
  [concept-type]
  (format "http://localhost:%s/translate/%s"
          (transmit-config/ingest-port)
          (name concept-type)))

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

(defn ingest-provider-url
  [provider-id]
  (format "http://localhost:%s/providers/%s" (transmit-config/ingest-port) provider-id))

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

(defn search-keywords-url
  "Returns the URL for retrieving controlled keywords."
  [keyword-scheme]
  (format "http://localhost:%s/keywords/%s" (transmit-config/search-port) (name keyword-scheme)))

(defn retrieve-concept-url
  ([type concept-id] (retrieve-concept-url type concept-id nil))
  ([type concept-id revision-id]
   (str "http://localhost:" (transmit-config/search-port) "/concepts/" concept-id
        (when revision-id (str "/" revision-id)))))

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

(defn bootstrap-root
  []
  (URL. "http" "localhost" (transmit-config/bootstrap-port) "/"))

(defn bootstrap-url
  "Returns a URL for a path in the bootstrap application."
  [path]
  (str (URL. (bootstrap-root) path)))

(defn- rebalance-collection-url
  [concept-id]
  (format "http://localhost:%s/rebalancing_collections/%s" (transmit-config/bootstrap-port) concept-id))

(defn start-rebalance-collection-url
  [concept-id]
  (format "%s/start" (rebalance-collection-url concept-id)))

(defn status-rebalance-collection-url
  [concept-id]
  (format "%s/status" (rebalance-collection-url concept-id)))

(defn finalize-rebalance-collection-url
  [concept-id]
  (format "%s/finalize" (rebalance-collection-url concept-id)))

(defn bulk-index-provider-url
  []
  (format "http://localhost:%s/bulk_index/providers" (transmit-config/bootstrap-port)))

(defn bulk-index-collection-url
  []
  (format "http://localhost:%s/bulk_index/collections" (transmit-config/bootstrap-port)))

(defn bulk-migrate-provider-url
  []
  (format "http://localhost:%s/bulk_migration/providers" (transmit-config/bootstrap-port)))

(defn bootstrap-health-url
  "URL to check bootstrap health."
  []
  (format "http://localhost:%s/health" (transmit-config/bootstrap-port)))

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

(defn full-refresh-collection-granule-aggregate-cache-url
  []
  (format "http://localhost:%s/jobs/trigger-full-collection-granule-aggregate-cache-refresh"
          (transmit-config/ingest-port)))

(defn partial-refresh-collection-granule-aggregate-cache-url
  []
  (format "http://localhost:%s/jobs/trigger-partial-collection-granule-aggregate-cache-refresh"
          (transmit-config/ingest-port)))

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

(defn indexer-reindex-tags-url
  "URL to reindex tags."
  []
  (format "http://localhost:%s/reindex-tags" (transmit-config/indexer-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Virtual Product URLs

(defn virtual-product-health-url
  "URL to check virtual product health."
  []
  (format "http://localhost:%s/health" (transmit-config/virtual-product-port)))

(defn virtual-product-translate-granule-entries-url
  "URL to translate virtual product entries to the corresponding source entries."
  []
  (format "http://localhost:%s/translate-granule-entries" (transmit-config/virtual-product-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Access Control URLs

(defn access-control-health-url
  "URL to check access control health."
  []
  (format "http://localhost:%s/health" (transmit-config/access-control-port)))

(defn access-control-clear-cache-url
  "Clear cache in access-control-app."
  []
  (format "http://localhost:%s/caches/clear-cache" (transmit-config/access-control-port)))

(defn access-control-read-caches-url
  "URL to read the access-control-caches."
  []
  (format "http://localhost:%s/caches" (transmit-config/access-control-port)))
