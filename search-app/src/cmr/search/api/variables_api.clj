(ns cmr.search.api.variables-api
  "Defines the API for associating/dissociating variables with collections in the CMR."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.services.variable-service :as variable-service]
   [compojure.core :refer :all]
   [compojure.route :as route]))

(defn- validate-variable-content-type
  "Validates that content type sent with a variable is JSON"
  [headers]
  (mt/extract-header-mime-type #{mt/json} headers "content-type" true))

(defn- variable-api-response
  "Creates a successful variable response with the given data response"
  ([data]
   (variable-api-response 200 data))
  ([status-code data]
   {:status status-code
    :body (json/generate-string (util/snake-case-data data))
    :headers {"Content-Type" mt/json}}))

(defn- verify-variable-association-permission
  "Verifies the current user has been granted permission to make variable associations"
  [context permission-type]
  (when-not (seq (acl/get-permitting-acls
                  context :system-object "INGEST_MANAGEMENT_ACL" permission-type))
    (errors/throw-service-error
      :unauthorized
      (format "You do not have permission to %s a variable." (name permission-type)))))

(defn associate-variable-to-collections
  "Associate the variable to a list of collections."
  [context headers body variable-name]
  (verify-variable-association-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-variable-content-type headers)
  (info (format "Associate variable [%s] on collections: %s by client: %s."
                variable-name body (:client-id context)))
  (variable-api-response
   (variable-service/associate-variable-to-collections context variable-name body)))

(defn dissociate-variable-to-collections
  "Dissociate the variable to a list of collections."
  [context headers body variable-name]
  (verify-variable-association-permission context :update)
  (common-enabled/validate-write-enabled context "search")
  (validate-variable-content-type headers)
  (info (format "Dissociating variable [%s] from collections: %s by client: %s."
                variable-name body (:client-id context)))
  (variable-api-response
   (variable-service/dissociate-variable-to-collections context variable-name body)))

(defn search-for-variables
  [context params]
  ;; placeholder for searching variables
  )

(def variable-api-routes
  (context "/variables" []

    ;; Search for variables
    (GET "/" {:keys [request-context params]}
      (search-for-variables request-context params))

    (context "/:variable-name" [variable-name]
      (context "/associations" []

        ;; Associate a variable with a list of collections
        (POST "/" {:keys [request-context headers body]}
          (associate-variable-to-collections
           request-context headers (slurp body) variable-name))

        ;; Dissociate a variable from a list of collections
        (DELETE "/" {:keys [request-context headers body]}
          (dissociate-variable-to-collections
           request-context headers (slurp body) variable-name))))))
