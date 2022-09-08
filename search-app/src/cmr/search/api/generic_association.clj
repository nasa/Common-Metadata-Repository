(ns cmr.search.api.generic-association
  "Defines common functions used by associations among generic concepts in the CMR."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.services.generic-association-service :as generic-assoc-service]
   [compojure.core :refer :all]))

(defn- validate-association-content-type
  "Validates that content type sent with a association is JSON."
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- api-response
  "Creates a successful association response with the given data response"
  ([data]
   (api-response 200 data))
  ([status-code data]
   {:status status-code
    :body (json/generate-string (util/snake-case-data data))
    :headers {"Content-Type" mt/json}}))

(defn- results-contain-errors?
  "Returns true if the results contain :errors"
  [results]
  (seq (filter #(some? (:errors %)) results)))

(defn- results->status-code
  "Return status code depending on if results contains error."
  [results]
  (if (results-contain-errors? results)
    400
    200)) 

(defn associate-concept-to-concepts
  "Associate the given concept by concept type and concept id to a list of
  concepts in the request body. User has to have update permission on INGEST_MANAGEMENT_ACL for the
  provider(s) of the concepts in order for the association(s) to be successful."
  [context headers body concept-id revision-id]
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Associate concept [%s] revision [%s] on concepts: %s by client: %s."
                concept-id revision-id body (:client-id context)))
  (let [concept-type (common-concepts/concept-id->type concept-id)
        results (generic-assoc-service/associate-to-concepts context concept-type concept-id revision-id body)
        status-code (results->status-code results)]
    (api-response status-code results)))

(defn dissociate-concept-from-concepts
  "Dissociate the given concept by concept type and concept id from a list of
  concepts in the request body."
  [context headers body concept-id revision-id]
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Dissociating concept [%s] revision [%s] from concepts: %s by client: %s."
                concept-id revision-id body (:client-id context)))
  (let [concept-type (common-concepts/concept-id->type concept-id)
        results (generic-assoc-service/dissociate-from-concepts context concept-type concept-id revision-id body)
        status-code (results->status-code results)]
    (api-response status-code results)))
