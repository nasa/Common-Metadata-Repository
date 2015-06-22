(ns cmr.metadata-db.services.search-service
  "Contains functions for retrieving concepts using parameter search"
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

(defn- find-providers-for-params
  "Find providers that mach the given parameters"
  [context params]
  ;; TODO - Add support for finding provider from concept-id parameter when support is added
  ;; to find-concepts for that parameter
  (if-let [provider-id (:provider-id params)]
    (if-let [provider (provider-service/get-provider-by-id context provider-id false)]
      [provider]
      [])
    (provider-service/get-providers context)))


(deftracefn find-concepts
  "Find concepts with specific parameters"
  [context params]
  (let [db (util/context->db context)
        latest-only? (= "true" (:latest params))
        providers (find-providers-for-params context params)
        params (dissoc params :latest)]
    (if (seq providers)
      (do
        (cv/validate-find-params params)
        (if latest-only?
          (c/find-latest-concepts db providers params)
          (c/find-concepts db providers params)))
      [])))