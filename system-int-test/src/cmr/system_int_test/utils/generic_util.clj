(ns cmr.system-int-test.utils.generic-util
  "Utility functions and definitions for use by generic document pipeline tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config]))

(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (jio/resource)
                   (slurp)
                   (json/parse-string true)))

(def data-quality-summary (-> "schemas/data-quality-summary/v1.0.0/metadata.json"
                              (jio/resource)
                              (slurp)
                              (json/parse-string true)))

(def order-option (-> "schemas/order-option/v1.0.0/metadata.json"
                      (jio/resource)
                      (slurp)
                      (json/parse-string true)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([token provider-id native-id concept-type]
   (generic-request token provider-id native-id concept-type nil :get))
  ([token provider-id native-id concept-type document method]
   (let [headers (if token
                   {"Accept" "application/json"
                    transmit-config/token-header token}
                   {"Accept" "application/json"})]
     (-> {:method method
          :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
          :connection-manager (sys/conn-mgr)
          :body (when document (json/generate-string document))
          :throw-exceptions false
          :headers headers}
         (client/request)))))

(defn ingest-generic-document
  "A wrapper function for generic-request, and returns the concept ingested."
  ([token provider-id native-id concept-type document]
   (ingest-generic-document token provider-id native-id concept-type document :get))
  ([token provider-id native-id concept-type document method]
   (json/parse-string
    (:body (generic-request
            token provider-id native-id (name concept-type) document method))
    true)))
