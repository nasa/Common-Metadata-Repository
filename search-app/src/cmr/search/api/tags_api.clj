(ns cmr.search.api.tags-api
  "Defines the API for tagging collections in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cmr.common.mime-types :as mt]
            [cmr.search.services.tagging.validation :as v]
            [cmr.search.services.tagging-service :as tagging-service]
            [cmr.common.services.errors :as errors]
            [cmr.acl.core :as acl]))

(defn- validate-tag-content-type
  "Validates that content type sent with a tag is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- tag-api-response
  "Creates a successful tag response with the given data response"
  ([data]
   (tag-api-response data true))
  ([data encode?]
   {:status 200
    :body (if encode? (json/generate-string data) data)
    :headers {"Content-Type" mt/json}}))

(defn- verify-tag-modification-permission
  "Verifies the current user has been granted permission to modify tags in ECHO ACLs"
  [context permission-type]
  (when-not (seq (acl/get-permitting-acls context :system-object "TAG_GROUP" permission-type))
    (errors/throw-service-error
      :unauthorized
      (format "You do not have permission to %s a tag." (name permission-type)))))

(defn- request-body->tag
  "Returns the tag in JSON from the given request body with the tag-key converted to lowercase."
  [body]
  (-> (json/parse-string body true)
      ;; tag-key is always in lowercase
      (update :tag-key str/lower-case)))

(defn create-tag
  "Processes a create tag request."
  [context headers body]
  (verify-tag-modification-permission context :create)
  (validate-tag-content-type headers)
  (v/validate-create-tag-json body)
  (->> body
       request-body->tag
       (tagging-service/create-tag context)
       tag-api-response))

(defn get-tag
  "Retrieves the tag with the given concept-id."
  [context concept-id]
  (-> (tagging-service/get-tag context concept-id)
      ;; We don't return the associated concept ids on the fetch response.
      ;; This could be changed if we wanted to. It's just not part of the requirements.
      (dissoc :associated-concept-ids)
      tag-api-response))

(defn update-tag
  "Processes a request to update a tag."
  [context headers body concept-id]
  (verify-tag-modification-permission context :update)
  (validate-tag-content-type headers)
  (v/validate-update-tag-json body)
  (->> body
       request-body->tag
       (tagging-service/update-tag context concept-id)
       tag-api-response))

(defn delete-tag
  "Deletes the tag with the given concept-id."
  [context concept-id]
  (verify-tag-modification-permission context :delete)
  (tag-api-response (tagging-service/delete-tag context concept-id)))

(defn associate-tag-to-collections
  "Associate the tag to a list of collections."
  [context headers body concept-id]
  (verify-tag-modification-permission context :update)
  (validate-tag-content-type headers)
  (tag-api-response (tagging-service/associate-tag-to-collections context concept-id body)))

(defn disassociate-tag-to-collections
  "Disassociate the tag to a list of collections."
  [context headers body concept-id]
  (verify-tag-modification-permission context :update)
  (validate-tag-content-type headers)
  (tag-api-response (tagging-service/disassociate-tag-to-collections context concept-id body)))

(defn associate-tag-by-query
  "Processes a request to associate a tag."
  [context headers body concept-id]
  (verify-tag-modification-permission context :update)
  (validate-tag-content-type headers)
  (tag-api-response (tagging-service/associate-tag-by-query context concept-id body)))

(defn disassociate-tag-by-query
  "Processes a request to disassociate a tag."
  [context headers body concept-id]
  (verify-tag-modification-permission context :update)
  (validate-tag-content-type headers)
  (tag-api-response (tagging-service/disassociate-tag-by-query context concept-id body)))

(defn search-for-tags
  [context params]
  (tag-api-response (tagging-service/search-for-tags context params) false))

(def tag-api-routes
  (context "/tags" []

    ;; Create a new tag
    (POST "/" {:keys [request-context headers body]}
      (create-tag request-context headers (slurp body)))

    ;; Search for tags
    (GET "/" {:keys [request-context params]}
      (search-for-tags request-context params))

    (context "/:tag-id" [tag-id]

      ;; Get a tag
      (GET "/" {:keys [request-context]}
        (get-tag request-context tag-id))

      ;; Delete a tag
      (DELETE "/" {:keys [request-context]}
        (delete-tag request-context tag-id))

      ;; Update a tag
      (PUT "/" {:keys [request-context headers body]}
        (update-tag request-context headers (slurp body) tag-id))

      (context "/associations" []

        ;; Associate a tag with a list of collections
        (POST "/" {:keys [request-context headers body]}
          (associate-tag-to-collections request-context headers (slurp body) tag-id))

        ;; Disassociate a tag with a list of collections
        (DELETE "/" {:keys [request-context headers body]}
          (disassociate-tag-to-collections request-context headers (slurp body) tag-id))

        (context "/by_query" []
          ;; Associate a tag with collections
          (POST "/" {:keys [request-context headers body]}
            (associate-tag-by-query request-context headers (slurp body) tag-id))

          ;; Disassociate a tag with collections
          (DELETE "/" {:keys [request-context headers body]}
            (disassociate-tag-by-query request-context headers (slurp body) tag-id)))))))
