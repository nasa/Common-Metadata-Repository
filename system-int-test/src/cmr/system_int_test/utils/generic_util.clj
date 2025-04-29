(ns cmr.system-int-test.utils.generic-util
  "Utility functions and definitions for use by generic document pipeline tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.mime-types :as mt]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config]))

(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (io/resource)
                   (slurp)
                   (json/parse-string true)))

(def data-quality-summary (-> "schemas/data-quality-summary/v1.0.0/metadata.json"
                              (io/resource)
                              (slurp)
                              (json/parse-string true)))

(def order-option (-> "schemas/order-option/v1.0.0/metadata.json"
                      (io/resource)
                      (slurp)
                      (json/parse-string true)))

(def collection-draft (-> "schemas/collection-draft/v1.0.0/metadata.json"
                          (io/resource)
                          (slurp)
                          (json/parse-string true)))

(def order-option-draft (-> "schemas/order-option-draft/v1.0.0/metadata.json"
                            (io/resource)
                            (slurp)
                            (json/parse-string true)))

(def variable-draft (-> "schemas/variable-draft/v1.0.0/metadata_with_private_data.json"
                        (io/resource)
                        (slurp)
                        (json/parse-string true)))

(def visualization (-> "schemas/visualization/v1.1.0/metadata.json"
                       (io/resource)
                       (slurp)
                       (json/parse-string true)))

(def visualization-draft (-> "schemas/visualization/v1.1.0/metadata.json"
                             (io/resource)
                             (slurp)
                             (json/parse-string true)))
(def citation (-> "schemas/citation/v1.0.0/metadata.json"
                       (io/resource)
                       (slurp)
                       (json/parse-string true)))

(def citation-draft (-> "schemas/citation-draft/v1.0.0/metadata.json"
                       (io/resource)
                       (slurp)
                       (json/parse-string true)))

(def variable-draft-without-private
  (-> "schemas/variable-draft/v1.0.0/metadata_with_private_data.json"
      (io/resource)
      (slurp)
      (json/parse-string true)
      (dissoc :_private)))

(defn grant-all-drafts-fixture
  "A test fixture that grants all users the ability to create and modify drafts."
  [providers guest-permissions registered-permissions]
  (fn [f]
    (let [providers (for [[provider-guid provider-id] providers]
                      {:provider-guid provider-guid
                       :provider-id provider-id})]
      (doseq [provider-map providers]
        ;; grant PROVIDER_CONTEXT permission for each provider.
        (echo-util/grant-provider-context (sys/context)
                                          (:provider-id provider-map)
                                          guest-permissions
                                          registered-permissions)))
    (f)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([token provider-id native-id concept-type]
   (generic-request token provider-id native-id concept-type nil :get))
  ([token provider-id native-id concept-type document method]
   (generic-request token provider-id native-id concept-type document method mt/umm-json))
  ([token provider-id native-id concept-type document method mime-type]
   (let [headers (if token
                   {"Accept" mt/json
                    "Content-Type" mime-type
                    transmit-config/token-header token}
                   {"Accept" mt/json
                    "Content-Type" mime-type})]
     (client/request
      {:method method
       :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
       :connection-manager (sys/conn-mgr)
       :body (when document
               (if (string/includes? mime-type "json")
                 (json/generate-string document)
                 document))
       :throw-exceptions false
       :headers headers}))))

(defn ingest-generic-document
  "A wrapper function for generic-request, and returns the concept ingested."
  ([token provider-id native-id concept-type document]
   (ingest-generic-document token provider-id native-id concept-type document :get))
  ([token provider-id native-id concept-type document method]
   (json/parse-string
    (:body (generic-request
            token provider-id native-id (name concept-type) document method))
    true)))

(defn ingest-generic-document-with-mime-type
  "A wrapper function for generic-request, and returns the concept ingested."
  [token provider-id native-id concept-type document method mime-type]
  (json/parse-string     
   (:body (generic-request
           token provider-id native-id (name concept-type) document method mime-type))
   true))
