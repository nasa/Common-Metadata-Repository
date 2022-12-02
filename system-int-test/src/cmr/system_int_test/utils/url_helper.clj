(ns cmr.system-int-test.utils.url-helper
  "helper to provide the urls to various service endpoints"
  (:require
   [cmr.common.config :as config]
   [cmr.elastic-utils.config :as es-config]
   [cmr.transmit.config :as transmit-config]
   [inflections.core :as inf]
   [ring.util.codec :as codec])
  (:import (java.net URL)))

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

(defn elastic-delete-tag-url
  [id]
  (format "%s/1_tags/_doc/%s" (elastic-root) id))

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
  (format "http://localhost:%s/jobs/old-revision-concept-cleanup"
          (transmit-config/metadata-db-port)))

(defn mdb-expired-concept-cleanup-url
  "URL to metadata db cleanup expired concepts job"
  []
  (format "http://localhost:%s/jobs/expired-concept-cleanup"
          (transmit-config/metadata-db-port)))

(defn mdb-service-association-search-url
  "URL to search service associations in metadata db."
  []
  (format "http://localhost:%s/concepts/search/service-associations"
          (transmit-config/metadata-db-port)))

(defn mdb-tool-association-search-url
  "URL to search tool associations in metadata db."
  []
  (format "http://localhost:%s/concepts/search/tool-associations"
          (transmit-config/metadata-db-port)))

(defn mdb-subscription-notification-time
  "URL to notification time in metadata db app"
  [concept-id]
  (format "http://localhost:%s/subscription/%s/notification-time"
          (transmit-config/metadata-db-port)
          concept-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ingest URLs

(defn enable-ingest-writes-url
  []
  (format "http://localhost:%s/enable-writes" (transmit-config/ingest-port)))

(defn disable-ingest-writes-url
  []
  (format "http://localhost:%s/disable-writes" (transmit-config/ingest-port)))

(defn reindex-suggestions-url
  "URL to reindex autocomplete suggestions"
  []
  (format "http://localhost:%s/jobs/reindex-autocomplete-suggestions"
          (transmit-config/ingest-port)))

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

(defn cleanup-granule-bulk-update-task-url
  []
  (format "http://localhost:%s/jobs/trigger-granule-task-cleanup-job"
          (transmit-config/ingest-port)))

(defn translate-metadata-url
  [concept-type]
  (format "http://localhost:%s/translate/%s"
          (transmit-config/ingest-port)
          (name concept-type)))


(defn ingest-url
  ([provider-id concept-type]
   (ingest-url provider-id concept-type nil))
  ([provider-id concept-type native-id]
   (let [url (if (and (nil? provider-id)
                      (= :subscription concept-type))
               (format "http://localhost:%s/subscriptions"
                     (transmit-config/ingest-port))
               (format "http://localhost:%s/providers/%s/%ss"
                     (transmit-config/ingest-port)
                     (codec/url-encode provider-id)
                     (name concept-type)))]
     (if native-id
       (str url "/" (codec/url-encode native-id))
       url))))

(defn ingest-subscription-url
  ([]
   (ingest-subscription-url nil))
  ([native-id]
   (let [url (format "http://localhost:%s/subscriptions"
                     (transmit-config/ingest-port))]
     (if native-id
       (str url "/" (codec/url-encode native-id))
       url))))

(defn ingest-variable-url
  ([coll-id native-id]
   (ingest-variable-url coll-id nil native-id))
  ([coll-id coll-revision-id native-id]
   (if coll-revision-id
     (format "http://localhost:%s/collections/%s/%s/variables/%s"
             (transmit-config/ingest-port)
             (codec/url-encode coll-id)
             (codec/url-encode coll-revision-id)
             (codec/url-encode native-id))
     (format "http://localhost:%s/collections/%s/variables/%s"
             (transmit-config/ingest-port)
             (codec/url-encode coll-id)
             (codec/url-encode native-id)))))

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

(defn ingest-collection-bulk-update-url
  "Bulk update collections"
  [provider-id]
  (format "http://localhost:%s/providers/%s/bulk-update/collections"
          (transmit-config/ingest-port)
          provider-id))

(defn ingest-granule-bulk-update-url
  "Bulk update granules"
  [provider-id]
  (format "http://localhost:%s/providers/%s/bulk-update/granules"
          (transmit-config/ingest-port)
          provider-id))

(defn ingest-collection-bulk-update-status-url
  "Get the tasks and statuses for collection bulk update by provider"
  [provider-id]
  (format "http://localhost:%s/providers/%s/bulk-update/collections/status"
          (transmit-config/ingest-port)
          provider-id))

(defn ingest-granule-bulk-update-status-url
  "Get the tasks and statuses for collection bulk update by provider"
  [provider-id]
  (format "http://localhost:%s/providers/%s/bulk-update/granules/status"
          (transmit-config/ingest-port)
          provider-id))

(defn ingest-collection-bulk-update-task-status-url
  "Get the task and collection statuses by provider and task"
  [provider-id task-id]
  (format "http://localhost:%s/providers/%s/bulk-update/collections/status/%s"
          (transmit-config/ingest-port)
          provider-id
          task-id))

(defn ingest-granule-bulk-update-task-status-url
  "Get the task and collection statuses by provider and task"
  ([]
   (format "http://localhost:%s/granule-bulk-update/status"
           (transmit-config/ingest-port)))
  ([task-id]
   (format "http://localhost:%s/granule-bulk-update/status/%s"
           (transmit-config/ingest-port)
           task-id)))

(defn ingest-generic-crud-url
  "Get the URL for Creating a Generic"
  [provider-id native-id]
  (format "http://localhost:%s/generics/provider/%s/%s"
          (transmit-config/ingest-port)
          provider-id
          native-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search URLs

(defn search-url
  [type]
  (format "http://localhost:%s/%ss" (transmit-config/search-port) (name type)))

(defn autocomplete-url
  "Autocomplete URL with query term and optional types collection"
  ([]
   (autocomplete-url nil))
  ([query]
   (if query
     (format "http://localhost:%s/autocomplete?%s" (transmit-config/search-port) query)
     (format "http://localhost:%s/autocomplete" (transmit-config/search-port)))))

(defn enable-search-writes-url
  []
  (format "http://localhost:%s/enable-writes" (transmit-config/search-port)))

(defn disable-search-writes-url
  []
  (format "http://localhost:%s/disable-writes" (transmit-config/search-port)))

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

(defn refresh-collection-metadata-cache-url
  []
  (format "http://localhost:%s/jobs/refresh-collection-metadata-cache"
          (transmit-config/search-port)))

(defn search-health-url
  "URL to check search health."
  []
  (format "http://localhost:%s/health" (transmit-config/search-port)))

(defn search-tile-url
  "URL to search for 2D grid tiles using input shapes"
  []
  (format "http://localhost:%s/tiles" (transmit-config/search-port)))

(defn search-deleted-collections-url
  "URL to search for deleted collections"
  []
  (format "http://localhost:%s/deleted-collections" (transmit-config/search-port)))

(defn search-deleted-granules-url
  "URL to search for deleted granules"
  []
  (format "http://localhost:%s/deleted-granules" (transmit-config/search-port)))

(defn provider-holdings-url
  "Returns the URL for retrieving provider holdings."
  []
  (format "http://localhost:%s/provider_holdings" (transmit-config/search-port)))

(defn search-keywords-url
  "Returns the URL for retrieving controlled keywords."
  ([keyword-scheme]
   (search-keywords-url keyword-scheme ""))
  ([keyword-scheme search-parameters]
   (format "http://localhost:%s/keywords/%s%s"
           (transmit-config/search-port)
           (name keyword-scheme)
           search-parameters)))

(defn retrieve-concept-url
  ([type concept-id] (retrieve-concept-url type concept-id nil))
  ([type concept-id revision-id]
   (str "http://localhost:" (transmit-config/search-port) "/concepts/" concept-id
        (when revision-id (str "/" revision-id)))))

(defn humanizers-report-url
  "URL to get the humanizers report"
  []
  (format "http://localhost:%s/humanizers/report" (transmit-config/search-port)))

(defn data-json-url
  "URL to get data.json."
  []
  (format "http://localhost:%s/socrata/data.json" (transmit-config/search-port)))

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

(defn search-site-url
  "Returns the url root for search site documentation."
  []
  (str (search-root) "site/"))

(defn search-site-providers-holdings-url
  "Returns the base url for the provider holdings for a collection"
  []
  (str (search-site-url) "collections/directory/"))

(defn clear-scroll-url
  "Returns the url for clearing a scroll context"
  []
  (str (search-root) "clear-scroll"))

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

(defn bulk-index-after-date-time-url
  [date-time]
  (format "http://localhost:%s/bulk_index/after_date_time?date_time=%s"
          (transmit-config/bootstrap-port)
          date-time))

(defn bulk-index-concepts-url
  []
  (format "http://localhost:%s/bulk_index/concepts" (transmit-config/bootstrap-port)))

(defn bulk-index-provider-url
  []
  (format "http://localhost:%s/bulk_index/providers" (transmit-config/bootstrap-port)))

(defn bulk-index-all-providers-url
  []
  (format "http://localhost:%s/bulk_index/providers/all" (transmit-config/bootstrap-port)))

(defn bulk-index-variables-url
  ([]
   (format "http://localhost:%s/bulk_index/variables" (transmit-config/bootstrap-port)))
  ([provider-id]
   (format "%s/%s" (bulk-index-variables-url) provider-id)))

(defn bulk-index-services-url
  ([]
   (format "http://localhost:%s/bulk_index/services" (transmit-config/bootstrap-port)))
  ([provider-id]
   (format "%s/%s" (bulk-index-services-url) provider-id)))

(defn bulk-index-tools-url
  ([]
   (format "http://localhost:%s/bulk_index/tools" (transmit-config/bootstrap-port)))
  ([provider-id]
   (format "%s/%s" (bulk-index-tools-url) provider-id)))

(defn bulk-index-subscriptions-url
  ([]
   (format "http://localhost:%s/bulk_index/subscriptions" (transmit-config/bootstrap-port)))
  ([provider-id]
   (format "%s/%s" (bulk-index-subscriptions-url) provider-id)))

(defn bulk-index-grids-url
  ([]
   (format "http://localhost:%s/bulk_index/grids" (transmit-config/bootstrap-port)))
  ([provider-id]
   (format "%s/%s" (bulk-index-grids-url) provider-id)))

(defn bulk-index-order-options-url
  ([]
   (format "http://localhost:%s/bulk_index/order-options" (transmit-config/bootstrap-port)))
  ([provider-id]
   (format "%s/%s" (bulk-index-order-options-url) provider-id)))

(defn bulk-index-generics-url
  ([concept-type]
   (format (str "http://localhost:%s/bulk_index/" (inf/plural concept-type)) (transmit-config/bootstrap-port)))
  ([concept-type provider-id]
   (format "%s/%s" (bulk-index-generics-url concept-type) provider-id)))

(defn bulk-index-collection-url
  []
  (format "http://localhost:%s/bulk_index/collections" (transmit-config/bootstrap-port)))

(defn bulk-migrate-provider-url
  []
  (format "http://localhost:%s/bulk_migration/providers" (transmit-config/bootstrap-port)))

(defn bulk-index-system-concepts-url
  []
  (format "http://localhost:%s/bulk_index/system_concepts" (transmit-config/bootstrap-port)))

(defn fingerprint-url
  ([]
   (format "http://localhost:%s/fingerprint/variables" (transmit-config/bootstrap-port)))
  ([concept-id]
   (format "%s/%s" (fingerprint-url) concept-id)))

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

(defn bootstrap-index-recently-replicated-url
  "URL to call the index recently replicated endpoint."
  []
  (format "http://localhost:%s/jobs/index_recently_replicated" (transmit-config/bootstrap-port)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indexer URLs

(defn indexer-reset-url
  "Delete and re-create indexes in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/reset" (transmit-config/indexer-port)))

(defn index-set-reset-url
  "Delete and re-create the index set in elastic. Only development team to use this functionality."
  []
  (format "http://localhost:%s/index-sets/reset" (transmit-config/indexer-port)))

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

(defn email-subscription-processing
  []
  (format "http://localhost:%s/jobs/trigger-email-subscription-processing"
          (transmit-config/ingest-port)))

(defn enable-email-subscription-processing
  []
  (format "http://localhost:%s/jobs/enable-email-subscription-processing-job"
          (transmit-config/ingest-port)))

(defn disable-email-subscription-processing
  []
  (format "http://localhost:%s/jobs/disable-email-subscription-processing-job"
          (transmit-config/ingest-port)))

(defn email-subscription-processing-job-state
  []
  (format "http://localhost:%s/jobs/email-subscription-processing-job-state"
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

(defn enable-access-control-writes-url
  "URL to enable writes in access control service."
  []
  (format "http://localhost:%s/enable-writes" (transmit-config/access-control-port)))

(defn disable-access-control-writes-url
  "URL to disable writes in access control service."
  []
  (format "http://localhost:%s/disable-writes" (transmit-config/access-control-port)))

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

(defn access-control-reindex-acls-url
  "URL to reindex acls."
  []
  (format "http://localhost:%s/reindex-acls" (transmit-config/access-control-port)))

(defn access-control-acls-url
  "URL to search or update acls"
  []
  (format "http://localhost:%s/acls" (transmit-config/access-control-port)))

(defn access-control-s3-buckets-url
  "URL to search permitted s3-buckets"
  []
  (format "http://localhost:%s/s3-buckets" (transmit-config/access-control-port)))

(defn indexer-url
  "URL to index a concept"
  []
  (format "http://localhost:%s" (transmit-config/indexer-port)))

(defn ingest-generic-crud-url
  "Get the URL for Creating a Generic Document"
  [concept-type provider-id native-id]
  ;; /providers/<provider-id>/<concept-type>/<native-id>
  ;; NOTE: concept-type must be "plural"
  (format "http://localhost:%s/providers/%s/%s/%s"
          (transmit-config/ingest-port)
          provider-id
          (inf/plural concept-type)
          native-id))
