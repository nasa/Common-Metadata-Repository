(ns cmr.search.api.association
  "Defines common functions used by associations with collections in the CMR."
  (:require
   [cheshire.core :as json]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.search.services.association-service :as assoc-service]
   [compojure.core :refer :all]
   [compojure.route :as route]))

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

(defn- verify-association-permission
  "Verifies the current user has been granted permission to make associations."
  [context concept-id permission-type]
  (let [provider-id (concepts/concept-id->provider-id concept-id)]
    (acl/verify-ingest-management-permission
     context :update :provider-object provider-id)))

(defn associate-concept-to-collections
  "Associate the given concept by concept type and concept id to a list of
  collections in the request body."
  [context headers body concept-type concept-id]
  (verify-association-permission context concept-id :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Associate %s [%s] on collections: %s by client: %s."
                (name concept-type) concept-id body (:client-id context)))
  (api-response
   (assoc-service/associate-to-collections context concept-type concept-id body)))

(defn dissociate-concept-from-collections
  "Dissociate the given concept by concept type and concept id from a list of
  collections in the request body."
  [context headers body concept-type concept-id]
  (verify-association-permission context concept-id :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Dissociating %s [%s] from collections: %s by client: %s."
                (name concept-type) concept-id body (:client-id context)))
  (api-response
   (assoc-service/dissociate-from-collections context concept-type concept-id body)))
