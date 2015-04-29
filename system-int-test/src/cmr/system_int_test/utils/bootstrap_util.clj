(ns cmr.system-int-test.utils.bootstrap-util
  "Contains utilities for working with the bootstrap application."
  (:require [cheshire.core :as json]
            [clojure.test :refer [is]]
            [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.metadata-db.config :as mdb-config]
            [cmr.bootstrap.test.catalog-rest :as cat-rest]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.util :as util]
            [cmr.system-int-test.system :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

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

(defn synchronize-databases
  "Tells the bootstrap application to synchronize Catalog REST and CMR."
  ([]
   (synchronize-databases {}))
  ([params]
   (let [{:keys [provider-id entry-title start-time end-time]} params
         start-time (when start-time (f/unparse (f/formatters :date-time) start-time))
         end-time (when end-time (f/unparse (f/formatters :date-time) end-time))
         query-params (util/remove-nil-keys
                        {:synchronous true
                         :start_date start-time
                         :end_date end-time
                         :provider_id provider-id
                         :entry_title entry-title})
         response (client/request {:method :post
                                   :url (url/db-synchronize-url)
                                   :query-params query-params
                                   :accept :json
                                   :throw-exceptions false
                                   :connection-manager (s/conn-mgr)})
         body (json/decode (:body response) true)]
     (assoc body :status (:status response)))))

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
        (ingest/create-provider guid provider-id {:cmr-only cmr-only})
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
    (try
      (db-fixture-setup provider-id-cmr-only-map)
      (f)
      (finally
        (db-fixture-tear-down provider-id-cmr-only-map)))))
