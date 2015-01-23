(ns cmr.bootstrap.services.validation
  "Provides functions to validate requested provider and/or collection of bulk_index operation exist."
  (:require [cmr.common.services.errors :as err]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.bootstrap.data.bulk-index :as bi]
            [cmr.metadata-db.services.provider-service :as provider-service]))

(defn- provider-exists?
  "Checks if the given provider exists in cmr."
  [context provider-id]
  (let [provider-list (provider-service/get-providers context)]
    (debug provider-list)
    (some #{provider-id} provider-list)))

(defn- collection-exists?
  "Checks if the given collection exists in cmr."
  [context provider-id collection-id]
  (let [coll-list (bi/get-provider-collection-list (:system context) provider-id)]
    (some #{collection-id} coll-list)))

(defn validate-provider
  "Validates to be bulk_indexed provider exists in cmr. Throws exceptions to send to the user."
  [context provider-id]
  (when-not (provider-exists? context provider-id)
    (err/throw-service-errors :bad-request
                              [(format "Providers: [%s] do not exist in the system" provider-id)])))

(defn validate-collection
  "Validates to be bulk_indexed collection exists in cmr. Throws exceptions to send to the user."
  [context provider-id collection-id]
  (validate-provider context provider-id)
  (when-not (collection-exists? context provider-id collection-id)
    (err/throw-service-errors :bad-request
                              [(format "Concept with concept-id [%s] and revision-id [null] does not exist."
                                       collection-id)])))


