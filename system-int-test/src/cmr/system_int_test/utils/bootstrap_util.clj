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
    [cmr.system-int-test.utils.url-helper :as url]))

(defn bulk-index-after-date-time
  "Call the bootstrap app to bulk index concepts with revision dates later than the given datetime."
  [date-time]
  (let [response (client/request
                   {:method :post
                    :query-params {:synchronous true}
                    :url (url/bulk-index-after-date-time-url date-time)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn bulk-index-provider
  "Call the bootstrap app to bulk index a provider."
  [provider-id]
  (let [response (client/request
                   {:method :post
                    :query-params {:synchronous true}
                    :url (url/bulk-index-provider-url)
                    :body (json/generate-string {:provider_id provider-id})
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn bulk-index-collection
  "Call the bootstrap app to bulk index a collection."
  [provider-id collection-id]
  (let [response (client/request
                   {:method :post
                    :query-params {:synchronous true}
                    :url (url/bulk-index-collection-url)
                    :body (json/generate-string {:provider_id provider-id :collection_id collection-id})
                    :content-type :json
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn start-rebalance-collection
  "Call the bootstrap app to kickoff rebalancing a collection."
  ([collection-id]
   (start-rebalance-collection collection-id true))
  ([collection-id synchronous]
   (let [response (client/request
                   {:method :post
                    :query-params {:synchronous synchronous}
                    :url (url/start-rebalance-collection-url collection-id)
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

(defn finalize-rebalance-collection
  "Call the bootstrap app to finalize rebalancing a collection."
  [collection-id]
  (let [response (client/request
                   {:method :post
                    :url (url/finalize-rebalance-collection-url collection-id)
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

(defn get-rebalance-status
  "Gets counts of granules in various places to check on bootstrap status."
  [collection-id]
  (let [response (client/request
                   {:method :get
                    :url (url/status-rebalance-collection-url collection-id)
                    :accept :json
                    :throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    (assoc body :status (:status response))))

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
