(ns cmr.search.api.tags
  "Defines the API for tagging collections in the CMR."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer (debug)]
   [cmr.search.api.association :as assoc]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.services.tagging-service :as tagging-service]
   [cmr.transmit.config :as transmit-config]
   [compojure.core :refer :all]))

(defn- validate-tag-content-type
  "Validates that content type sent with a tag is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- has-error?
  "Whether there's an error in [data]."
  [data]
  (some #(contains? % :errors) data))

(defn tag-association-results->status-code
  "Check for concept-types requiring error status to be returned.
  If the concept-type is error-sensitive the function will check for any errors in the results.
  Will return:
  - 200 OK -- if response has no errors
  - 207 MULTI-STATUS -- if response has some errors and some successes
  - 400 BAD REQUEST -- if response has all errors"
  [results]
    (let [result-count (count results)
          num-errors (assoc/num-errors-in-assoc-results results)]
      (cond
        (zero? result-count) 200
        (= num-errors result-count) 400
        (pos? num-errors) 207
        :else 200)))

(defn tag-api-response
  "Creates a successful tag response with the given data response"
  ([data]
   (if (has-error? data)
     (tag-api-response 400 data)
     (tag-api-response 200 data)))
  ([status-code data]
   (let [data-val (if (= 207 status-code)
                    (assoc/add-individual-statuses data)
                    data)]
   {:status status-code
    :body (json/generate-string (util/snake-case-data data-val))
    :headers {"Content-Type" mt/json}})))

(defn- verify-tag-modification-permission
  "Verifies the current user has been granted permission to modify tags in ECHO ACLs"
  [context permission-type]
  (when-not (or (transmit-config/echo-system-token? context)
                (seq (acl/get-permitting-acls context :system-object "TAG_GROUP" permission-type)))
    (errors/throw-service-error
     :unauthorized
     (format "You do not have permission to %s a tag." (name permission-type)))))

(defn create-tag
  "Processes a create tag request."
  [context headers body]
  (verify-tag-modification-permission context :create)
  (common-enabled/validate-write-enabled context "search")
  (validate-tag-content-type headers)
  (let [result (tagging-service/create-tag context body)
        status-code (if (= 1 (:revision-id result)) 201 200)]
    (tag-api-response status-code result)))

(defn get-tag
  "Retrieves the tag with the given tag-key."
  [context tag-key]
  (tag-api-response (tagging-service/get-tag context tag-key)))

(defn update-tag
  "Processes a request to update a tag."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-tag-content-type headers)
  (tag-api-response (tagging-service/update-tag context tag-key body)))

(defn delete-tag
  "Deletes the tag with the given tag-key."
  [context tag-key]
  (common-enabled/validate-write-enabled context "search")
  (verify-tag-modification-permission context :delete)
  (tag-api-response (tagging-service/delete-tag context tag-key)))

(defn associate-tag-to-collections
  "Associate the tag to a list of collections."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-tag-content-type headers)
  (debug (format "Tagging [%s] on collections: %s by client: %s."
                tag-key body (:client-id context)))
  (let [result (tagging-service/associate-tag-to-collections context tag-key body)
        status-code (tag-association-results->status-code result)]
      (tag-api-response status-code result)))

(defn dissociate-tag-to-collections
  "Dissociate the tag to a list of collections."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-tag-content-type headers)
  (debug (format "Dissociating tag [%s] from collections: %s by client: %s."
                tag-key body (:client-id context)))
  (let [result (tagging-service/dissociate-tag-to-collections context tag-key body)
        status-code (tag-association-results->status-code result)]
    (tag-api-response status-code result)))

(defn associate-tag-by-query
  "Processes a request to associate a tag."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-tag-content-type headers)
  (debug (format "Tagging [%s] on collections by query: %s by client: %s."
                tag-key body (:client-id context)))
  (tag-api-response (tagging-service/associate-tag-by-query context tag-key body)))

(defn dissociate-tag-by-query
  "Processes a request to dissociate a tag."
  [context headers body tag-key]
  (verify-tag-modification-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-tag-content-type headers)
  (debug (format "Dissociating tag [%s] from collections by query: %s by client: %s."
                tag-key body (:client-id context)))
  (tag-api-response (tagging-service/dissociate-tag-by-query context tag-key body)))

(defn search-for-tags
  [context params]
  (tag-api-response (tagging-service/search-for-tags context params)))

(def tag-api-routes
  (context "/tags" []

    ;; Create a new tag
    (POST "/" request
      (let [{:keys [request-context headers body]} request]
        (create-tag request-context headers (slurp body))))

    ;; Search for tags
    (GET "/" {:keys [request-context params]}
      (search-for-tags request-context params))

    (context "/:tag-key" [tag-key]

      ;; Get a tag
      (GET "/" {:keys [request-context]}
        (get-tag request-context (string/lower-case tag-key)))

      ;; Delete a tag
      (DELETE "/" request
        (let [{:keys [request-context]} request]
          (delete-tag request-context (string/lower-case tag-key))))

      ;; Update a tag
      (PUT "/" request
        (let [{:keys [request-context headers body]} request]
          (update-tag request-context headers (slurp body) (string/lower-case tag-key))))

      (context "/associations" []

        ;; Associate a tag with a list of collections
        (POST "/" request
          (let [{:keys [request-context headers body]} request]
            (associate-tag-to-collections
             request-context headers (slurp body) (string/lower-case tag-key))))

        ;; Dissociate a tag with a list of collections
        (DELETE "/" request
          (let [{:keys [request-context headers body]} request]
            (dissociate-tag-to-collections
             request-context headers (slurp body) (string/lower-case tag-key))))

        (context "/by_query" []
          ;; Associate a tag with collections
          (POST "/" request
            (let [{:keys [request-context headers body]} request]
              (associate-tag-by-query
               request-context headers (slurp body) (string/lower-case tag-key))))

          ;; Dissociate a tag with collections
          (DELETE "/" request
            (let [{:keys [request-context headers body]} request]
              (dissociate-tag-by-query
               request-context headers (slurp body) (string/lower-case tag-key)))))))))
