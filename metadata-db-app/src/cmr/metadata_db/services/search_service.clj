(ns cmr.metadata-db.services.search-service
  "Contains fucntions for retrieving concepts using parameter search"
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.util :as util]
            [cmr.metadata-db.services.concept-validations :as cv]
            [cmr.metadata-db.services.provider-service :as provider-service]

            ;; Required to get code loaded
            [cmr.metadata-db.data.oracle.concepts]
            [cmr.metadata-db.data.oracle.concepts.collection]
            [cmr.metadata-db.data.oracle.concepts.granule]
            [cmr.metadata-db.data.oracle.providers]

            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn find-concepts-for-provider
  "Find concepts for a concept type with specific parameters"
  [context params]
  (let [db (util/context->db context)
        latest-only? (= "true" (:latest params))
        params (dissoc params :latest)]
    (cv/validate-find-params params)
    ;; provider-id is a required field in find params. It always exists.
    (if-let [provider (provider-service/get-provider-by-id context (:provider-id params) false)]
      (if latest-only?
        (c/find-latest-concepts db [provider] params)
        (c/find-concepts db [provider] params))
      ;; the provider doesn't exist
      [])))

(deftracefn find-concepts
  "Find concepts for all providers for a concept type with specific parameters"
  [context params]
  (let [db (util/context->db context)
        latest-only? (= "true" (:latest params))
        params (dissoc params :latest :provider-id)
        providers (provider-service/get-providers context)]
    (cv/validate-find-params params)
    (if latest-only?
      (c/find-latest-concepts db providers params)
      (c/find-concepts db providers params))))