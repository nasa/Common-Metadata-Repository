(ns cmr.system-int-test.utils.bootstrap-util
  "Contains utilities for working with the bootstrap application."
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clj-time.core :as t]
    [clj-time.format :as f]
    [clojure.test :refer [is]]
    [cmr.bootstrap.test.catalog-rest :as cat-rest]
    [cmr.common.lifecycle :as lifecycle]
    [cmr.common.util :as util]
    [cmr.metadata-db.config :as mdb-config]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.url-helper :as url]
    [cmr.transmit.config :as transmit-config]))

(defn bulk-index-after-date-time
  "Call the bootstrap app to bulk index concepts with revision dates later than the given datetime."
  ([date-time]
   (bulk-index-after-date-time date-time {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([date-time headers]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-after-date-time-url date-time)
                     :content-type :json
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response))))
  ([date-time headers provider-ids]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-after-date-time-url date-time)
                     :body (json/generate-string {:provider_ids provider-ids})
                     :content-type :json
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn bulk-index-concepts
  "Call the bootstrap app to bulk index concepts by id."
  ([provider-id concept-type concept-ids]
   (bulk-index-concepts provider-id concept-type concept-ids {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id concept-type concept-ids headers]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-concepts-url)
                     :body (json/generate-string {:provider_id provider-id
                                                  :concept_type concept-type
                                                  :concept_ids concept-ids})
                     :content-type :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn bulk-delete-concepts
  "Call the bootstrap app to bulk delete concepts by id."
  ([provider-id concept-type concept-ids]
   (bulk-delete-concepts
     provider-id concept-type concept-ids {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id concept-type concept-ids headers]
   (let [response (client/request
                    {:method :delete
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-concepts-url)
                     :body (json/generate-string {:provider_id provider-id
                                                  :concept_type concept-type
                                                  :concept_ids concept-ids})
                     :content-type :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn- bulk-index-by-url
  "Calls bootstrap app on the given bulk index url"
  ([bulk-index-url headers]
   (let [response (client/request
                   {:method :post
                    :headers headers
                    :url bulk-index-url
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn bulk-index-variables
  "Call the bootstrap app to bulk index variables (either all of them, or just the
  ones for the given provider)."
  ([]
   (bulk-index-variables {transmit-config/token-header (transmit-config/echo-system-token)} nil nil))
  ([headers _ _]
   (bulk-index-by-url (url/bulk-index-variables-url) headers))
  ([provider-id]
   (bulk-index-variables provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (bulk-index-by-url (url/bulk-index-variables-url provider-id) headers)))

(defn bulk-index-services
  "Call the bootstrap app to bulk index services (either all of them, or just the
  ones for the given provider)."
  ([]
   (bulk-index-services {transmit-config/token-header (transmit-config/echo-system-token)} nil nil))
  ([headers _ _]
   (bulk-index-by-url (url/bulk-index-services-url) headers))
  ([provider-id]
   (bulk-index-services provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (bulk-index-by-url (url/bulk-index-services-url provider-id) headers)))

(defn bulk-index-tools
  "Call the bootstrap app to bulk index tools (either all of them, or just the
  ones for the given provider)."
  ([]
   (bulk-index-tools {transmit-config/token-header (transmit-config/echo-system-token)} nil nil))
  ([headers _ _]
   (bulk-index-by-url (url/bulk-index-tools-url) headers))
  ([provider-id]
   (bulk-index-tools provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (bulk-index-by-url (url/bulk-index-tools-url provider-id) headers)))

(defn bulk-index-subscriptions
  "Call the bootstrap app to bulk index subscriptions (either all of them, or just the
  ones for the given provider)."
  ([]
   (bulk-index-subscriptions {transmit-config/token-header (transmit-config/echo-system-token)} nil nil))
  ([headers _ _]
   (bulk-index-by-url (url/bulk-index-subscriptions-url) headers))
  ([provider-id]
   (bulk-index-subscriptions provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (bulk-index-by-url (url/bulk-index-subscriptions-url provider-id) headers)))

(defn bulk-index-grids
  "Call the bootstrap app to bulk index grids (either all of them, or just the
  ones for the given provider)."
  ([]
   (bulk-index-grids nil {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id]
   (bulk-index-grids provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (bulk-index-by-url
    (if (nil? provider-id)
      (url/bulk-index-grids-url)
      (url/bulk-index-grids-url provider-id))
    headers)))

(defn bulk-index-order-options
  "Call the bootstrap app to bulk index order options (either all of them, or just the
  ones for the given provider)."
  ([]
   (bulk-index-order-options nil {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id]
   (bulk-index-order-options provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (bulk-index-by-url
    (if (nil? provider-id)
      (url/bulk-index-order-options-url)
      (url/bulk-index-order-options-url provider-id))
    headers)))

(defn bulk-index-generics
  "Call the bootstrap app to bulk index a generic (either all of them, or just the
  ones for the given provider)."
  ([concept-type]
   (bulk-index-generics concept-type nil {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([concept-type provider-id]
   (bulk-index-generics concept-type provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([concept-type provider-id headers]
   (bulk-index-by-url
    (if (nil? provider-id)
      (url/bulk-index-generics-url (name concept-type))
      (url/bulk-index-generics-url (name concept-type) provider-id))
    headers)))

(defn bulk-index-provider
  "Call the bootstrap app to bulk index a provider."
  ([provider-id]
   (bulk-index-provider provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-provider-url)
                     :body (json/generate-string {:provider_id provider-id})
                     :content-type :json
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn bulk-index-all-providers
  "Call the bootstrap app to bulk index all providers."
  ([]
   (bulk-index-all-providers nil))
  ([headers]
   (let [response (client/request
                   {:method :post
                    :headers headers
                    :query-params {:synchronous true}
                    :url (url/bulk-index-all-providers-url)
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn bulk-index-collection
  "Call the bootstrap app to bulk index a collection."
  ([provider-id collection-id]
   (bulk-index-collection provider-id collection-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id collection-id headers]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-collection-url)
                     :body (json/generate-string {:provider_id provider-id :collection_id collection-id})
                     :content-type :json
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn- fingerprint-by-url
  "Calls bootstrap app on the given fingerprint url"
  ([fingerprint-url headers]
   (fingerprint-by-url fingerprint-url headers nil))
  ([fingerprint-url headers provider-id]
   (let [body (when provider-id
                (json/generate-string {:provider_id provider-id}))
         response (client/request {:method :post
                                   :headers headers
                                   :query-params {:synchronous true}
                                   :url fingerprint-url
                                   :body body
                                   :content-type :json
                                   :throw-exceptions false
                                   :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn fingerprint-variable-by-concept-id
  "Call the bootstrap app to update variable fingerprint specified by the given concept-id."
  ([concept-id]
   (fingerprint-variable-by-concept-id
    concept-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([concept-id headers]
   (fingerprint-by-url (url/fingerprint-url concept-id) headers)))

(defn fingerprint-variables-by-provider
  "Call the bootstrap app to update fingerprints of variables for the given provider."
  ([provider-id]
   (fingerprint-variables-by-provider
    provider-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([provider-id headers]
   (fingerprint-by-url (url/fingerprint-url) headers provider-id)))

(defn fingerprint-all-variables
  "Call the bootstrap app to update fingerprints of all variables."
  ([]
   (fingerprint-all-variables
    {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([headers]
   (fingerprint-by-url (url/fingerprint-url) headers)))

(defn start-rebalance-collection
  "Call the bootstrap app to kickoff rebalancing a collection."
  ([collection-id]
   (start-rebalance-collection collection-id {}))
  ([collection-id options]
   (let [synchronous (get options :synchronous true)
         target (get options :target "separate-index")
         headers (get options :headers {transmit-config/token-header (transmit-config/echo-system-token)})
         response (client/request
                   {:method :post
                    :query-params {:synchronous synchronous
                                   :target target}
                    :headers headers
                    :url (url/start-rebalance-collection-url collection-id)
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn finalize-rebalance-collection
  "Call the bootstrap app to finalize rebalancing a collection."
  ([collection-id]
   (finalize-rebalance-collection
     collection-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([collection-id headers]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :url (url/finalize-rebalance-collection-url collection-id)
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn get-rebalance-status
  "Gets counts of granules in various places to check on bootstrap status."
  ([collection-id]
   (get-rebalance-status collection-id {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([collection-id headers]
   (let [response (client/request
                    {:method :get
                     :headers headers
                     :url (url/status-rebalance-collection-url collection-id)
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn bulk-migrate-provider
  "Call the bootstrap app to bulk db migrate a provider."
  [provider-id]
  (let [response (client/request
                   {:method :post
                    :url (url/bulk-migrate-provider-url)
                    :query-params {:synchronous true}
                    :body (format "{\"provider_id\": \"%s\"}" provider-id)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn bulk-migrate-providers
  "Bulk migrates all the providers. Assumes they should all be successful"
  [& provider-ids]
  (doseq [provider-id provider-ids]
    (is (= 202
           (:status (bulk-migrate-provider provider-id))))))

(defn bulk-index-providers
  "Bulk indexes all the providers. Assumes they should all be successful"
  [& provider-ids]
  (doseq [provider-id provider-ids]
    (is (= 202
           (:status (bulk-index-provider provider-id))))))

(defn bulk-index-system-concepts
  "Bulk index all the acls, access-groups, and tags."
  ([]
   (bulk-index-system-concepts {transmit-config/token-header (transmit-config/echo-system-token)}))
  ([headers]
   (let [response (client/request
                    {:method :post
                     :headers headers
                     :query-params {:synchronous true}
                     :url (url/bulk-index-system-concepts-url)
                     :accept :json
                     :throw-exceptions false
                     :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn index-recently-replicated
  "Calls the index-recently-replicated endpoint to index all recently replicated concepts."
  []
  (client/request {:method :post
                   :url (url/bootstrap-index-recently-replicated-url)
                   :throw-exceptions false
                   :connection-manager (s/conn-mgr)}))

(defn bootstrap-virtual-products
  "Call the bootstrap app to bulk index a collection."
  [provider-id entry-title]
  (let [response (client/request
                   {:method :post
                    :query-params (util/remove-nil-keys {:synchronous true
                                                         :provider-id provider-id
                                                         :entry-title entry-title})
                    :url (url/bootstrap-url "virtual_products")
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn system
  "Returns a system suitable for calling the Catalog REST test code to create providers and add concepts."
  []
  {:db (:bootstrap-db (s/system))
   :catalog-rest-user (mdb-config/catalog-rest-db-username)})

(defn db-fixture-setup
  [provider-id-cmr-only-map]
  (s/only-with-real-database
    (let [system (system)]
      (dev-sys-util/reset)
      (doseq [[provider-id cmr-only] provider-id-cmr-only-map
              :let [guid (str provider-id "-guid")]]
        (ingest/create-provider {:provider-guid guid :provider-id provider-id :cmr-only cmr-only})
        (when-not cmr-only
          (cat-rest/create-provider system provider-id))))))

(defn db-fixture-tear-down
  [provider-id-cmr-only-map]
  (s/only-with-real-database
    (let [system (system)]
      (dev-sys-util/reset)
      ;; Delete catalog rest providers
      (doseq [[provider-id cmr-only] provider-id-cmr-only-map
              :when (not cmr-only)]
        (cat-rest/delete-provider system provider-id)))))

(defn db-fixture
  "This is a fixture that sets up things for bootstrap database integration tests. It resets the CMR,
  then creates CMR providers and Catalog REST providers. All data is cleaned up at the end.
  If we're not connected to a real database then the setup is skipped."
  [provider-id-cmr-only-map]
  (fn [f]
    (db-fixture-setup provider-id-cmr-only-map)
    (try
      (f)
      (finally
        (db-fixture-tear-down provider-id-cmr-only-map)))))

(defn clear-caches
  "Clears caches in the bootstrap application"
  []
  (client/post (url/bootstrap-clear-cache-url)
               {:connection-manager (s/conn-mgr)
                :headers {transmit-config/token-header (transmit-config/echo-system-token)}}))
