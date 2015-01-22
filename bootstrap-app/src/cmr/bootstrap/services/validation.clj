(ns cmr.bootstrap.services.validation
  "Provides functions to validate requested provider and/or collection of bulk_index operation exist."
  (:require [cmr.common.services.errors :as err]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.log :refer (debug info warn error)]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.set :as cset]))

(defn- providers-validation
  "Validates provider(s) supplied in bulk_index operation exist in the CMR."
  [provider-ids]
  (let [provider-ids-set (if (list? provider-ids) (into #{} provider-ids) (set (list provider-ids)))
        providers-url (format "http://localhost:%s/providers" (transmit-config/metadata-db-port))
        cmr-providers-set (set (-> (client/get providers-url) :body (json/decode true) :providers))]
    (if (and (> (count provider-ids-set) 0)
             (cset/subset? provider-ids-set cmr-providers-set))
      []
      [(format "Providers: [%s] do not exist in the system" provider-ids)])))

(defn- collection-validation
  "Validates collection supplied in bulk_index operation exists in cmr."
  [collection-id]
  (let [collection-url (format "http://localhost:%s/concepts/%s"
                               (transmit-config/metadata-db-port) collection-id)
        errors (-> (client/get collection-url) :body (json/decode true) :errors)]
    (if (empty? errors) [] errors)))

(defn validate-providers-exist
  "Validates to be bulk_indexed provider(s) exist in cmr. Throws exceptions to send to the user."
  [provider-ids]
  (let [errors (providers-validation provider-ids)]
    (when (seq errors)
      (err/throw-service-errors :bad-request errors))))

(defn validate-collection-exists
  "Validates to be bulk_indexed collection exists in cmr. Throws exceptions to send to the user."
  [provider-id collection-id]
  (let [provider-errors (providers-validation provider-id)
        collection-url (format "http://localhost:%s/concepts/%s"
                               (transmit-config/metadata-db-port) collection-id)
        resp-body (try
                    (json/decode ((client/get collection-url) :body) true)
                    (catch clojure.lang.ExceptionInfo e
                      (let [body (json/decode (get-in (ex-data e) [:object :body]) true)]
                        body)))
        coll-errors (resp-body :errors)
        errors (concat provider-errors coll-errors)]
    (when (seq errors)
      (err/throw-service-errors :bad-request errors))))


