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
   [cmr.search.services.association-validation :as assoc-validation]
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

(defn- results-contain-errors?
  "Returns true if the results contain :errors"
  [results]
  (seq (filter #(some? (:errors %)) results)))

(defmulti association-results->status-code
  "Check for concept-types requiring error status to be returned. This is currently :service and :variable
  If the concept-type is error-sensitive the function will check for any errors in the results, and will return 400 if
  any are errors are present. Otherwise it will return 200"
  (fn [concept-type results]
    (when (some #{concept-type} '(:variable :service :tool))
      :error-sensitive)))

(defmethod association-results->status-code :default
  [_ _]
  200)

(defmethod association-results->status-code :error-sensitive
  [_ results]
  (if (results-contain-errors? results)
    400
    200))

(defn associate-concept-to-collections
  "Associate the given concept by concept type and concept id to a list of
  collections in the request body. User has to have update permission on INGEST_MANAGEMENT_ACL for the
  provider(s) of the collection(s) in order for the association(s) to be successful."
  [context headers body concept-type concept-id]
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Associate %s [%s] on collections: %s by client: %s."
                (name concept-type) concept-id body (:client-id context)))
  (if (and (> (count (assoc-validation/associations-json->associations body)) 1)
           (= :variable concept-type))
    (api-response
      400
      {:error "Only one collection allowed in the list because a variable can only be associated with one collection."})
    (let [results (assoc-service/associate-to-collections context concept-type concept-id body)
          status-code (association-results->status-code concept-type results)]
      (api-response status-code results))))

(defn dissociate-concept-from-collections
  "Dissociate the given concept by concept type and concept id from a list of
  collections in the request body."
  [context headers body concept-type concept-id]
  (common-enabled/validate-write-enabled context "search")
  (validate-association-content-type headers)
  (info (format "Dissociating %s [%s] from collections: %s by client: %s."
                (name concept-type) concept-id body (:client-id context)))
  (if (and (> (count (map :concept-id (json/parse-string body true))) 1)
           (= :variable concept-type)) 
    (api-response
      400
      {:error "Only one variable at a time may be dissociated."})
    (let [results (assoc-service/dissociate-from-collections context concept-type concept-id body)
          status-code (association-results->status-code concept-type results)]
      (api-response status-code results))))
