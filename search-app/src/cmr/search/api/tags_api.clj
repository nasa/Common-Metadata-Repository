(ns cmr.search.api.tags-api
  "Defines the API for tagging collections in the CMR."
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.common-app.api.enabled :as common-enabled]
            [cmr.search.services.tagging-service :as tagging-service]
            [cmr.acl.core :as acl]))

(defn- validate-tag-content-type
  "Validates that content type sent with a tag is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- snake-case-data
  "Returns the given data with keys converted to snake case."
  [data]
  (cond
    (sequential? data) (map util/map-keys->snake_case data)
    (map? data) (util/map-keys->snake_case data)
    :else data))

(defn- tag-api-response
  "Creates a successful tag response with the given data response"
  ([data]
   (tag-api-response 200 data))
  ([status-code data]
   {:status status-code
    :body (json/generate-string (snake-case-data data))
    :headers {"Content-Type" mt/json}}))

(defn- verify-tag-modification-permission
  "Verifies the current user has been granted permission to modify tags in ECHO ACLs"
  [context permission-type]
  (when-not (seq (acl/get-permitting-acls context :system-object "TAG_GROUP" permission-type))
    (errors/throw-service-error
      :unauthorized
      (format "You do not have permission to %s a tag." (name permission-type)))))

(defn create-tag
  "Processes a create tag request."
  [context headers body]
  (verify-tag-modification-permission context :create)
  (if (common-enabled/app-enabled? context)
   (do
     (validate-tag-content-type headers)
     (let [result (tagging-service/create-tag context body)
           status-code (if (= 1 (:revision-id result)) 201 200)]
       (tag-api-response status-code result)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))

(defn get-tag
  "Retrieves the tag with the given tag-key."
  [context tag-key]
  (tag-api-response (tagging-service/get-tag context tag-key)))

(defn update-tag
  "Processes a request to update a tag."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (if (common-enabled/app-enabled? context)
   (do
     (validate-tag-content-type headers)
     (tag-api-response (tagging-service/update-tag context tag-key body)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))

(defn delete-tag
  "Deletes the tag with the given tag-key."
  [context tag-key]
  (if (common-enabled/app-enabled? context)
   (do
     (verify-tag-modification-permission context :delete)
     (tag-api-response (tagging-service/delete-tag context tag-key)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))

(defn associate-tag-to-collections
  "Associate the tag to a list of collections."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (if (common-enabled/app-enabled? context)
   (do
     (validate-tag-content-type headers)
     (tag-api-response (tagging-service/associate-tag-to-collections context tag-key body)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))

(defn disassociate-tag-to-collections
  "Disassociate the tag to a list of collections."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (if (common-enabled/app-enabled? context)
   (do
     (validate-tag-content-type headers)
     (tag-api-response (tagging-service/disassociate-tag-to-collections context tag-key body)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))

(defn associate-tag-by-query
  "Processes a request to associate a tag."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (if (common-enabled/app-enabled? context)
   (do
     (validate-tag-content-type headers)
     (tag-api-response (tagging-service/associate-tag-by-query context tag-key body)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))
(defn disassociate-tag-by-query
  "Processes a request to disassociate a tag."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (if (common-enabled/app-enabled? context)
   (do
     (validate-tag-content-type headers)
     (tag-api-response (tagging-service/disassociate-tag-by-query context tag-key body)))
   (errors/throw-service-error :service-unavailable
                               (common-enabled/service-disabled-message "search"))))

(defn search-for-tags
  [context params]
  (tag-api-response (tagging-service/search-for-tags context params)))

(def tag-api-routes
  (context "/tags" []

    ;; Create a new tag
    (POST "/" {:keys [request-context headers body]}
      (create-tag request-context headers (slurp body)))

    ;; Search for tags
    (GET "/" {:keys [request-context params]}
      (search-for-tags request-context params))

    (context "/:tag-key" [tag-key]

      ;; Get a tag
      (GET "/" {:keys [request-context]}
        (get-tag request-context (str/lower-case tag-key)))

      ;; Delete a tag
      (DELETE "/" {:keys [request-context]}
        (delete-tag request-context (str/lower-case tag-key)))

      ;; Update a tag
      (PUT "/" {:keys [request-context headers body]}
        (update-tag request-context headers (slurp body) (str/lower-case tag-key)))

      (context "/associations" []

        ;; Associate a tag with a list of collections
        (POST "/" {:keys [request-context headers body]}
          (associate-tag-to-collections
            request-context headers (slurp body) (str/lower-case tag-key)))

        ;; Disassociate a tag with a list of collections
        (DELETE "/" {:keys [request-context headers body]}
          (disassociate-tag-to-collections
            request-context headers (slurp body) (str/lower-case tag-key)))

        (context "/by_query" []
          ;; Associate a tag with collections
          (POST "/" {:keys [request-context headers body]}
            (associate-tag-by-query
              request-context headers (slurp body) (str/lower-case tag-key)))

          ;; Disassociate a tag with collections
          (DELETE "/" {:keys [request-context headers body]}
            (disassociate-tag-by-query
              request-context headers (slurp body) (str/lower-case tag-key))))))))
