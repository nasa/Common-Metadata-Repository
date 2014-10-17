(ns cmr.metadata-db.api.routes
  "Defines the HTTP URL routes for the application."
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.string :as str]
            [ring.util.response :as r]
            [ring.util.codec :as codec]
            [ring.middleware.json :as ring-json]
            [clojure.stacktrace :refer [print-stack-trace]]
            [cheshire.core :as json]
            [cmr.common.jobs :as jobs]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.api.errors :as errors]
            [cmr.common.services.errors :as serv-err]
            [cmr.common.cache :as cache]
            [cmr.system-trace.http :as http-trace]
            [cmr.metadata-db.services.concept-service :as concept-service]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.metadata-db.services.health-service :as hs]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.acl.core :as acl]
            [inflections.core :as inf]))

(def cache-api-routes
  "Create routes for the cache querying/management api"
  (context "/caches" []
    ;; Get the list of caches
    (GET "/" {:keys [params request-context headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [caches (map name (keys (get-in context [:system :caches])))]
          (acl/verify-ingest-management-permission context :read)
          {:status 200
           :body (json/generate-string caches)})))
    ;; Get the keys for the given cache
    (GET "/:cache-name" {{:keys [cache-name] :as params} :params
                         request-context :request-context
                         headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [cache (cache/context->cache context (keyword cache-name))]
          (when cache
            (let [result (cache/cache-keys cache)]
              {:status 200
               :body (json/generate-string result)})))))

    ;; Get the value for the given key for the given cache
    (GET "/:cache-name/:cache-key" {{:keys [cache-name cache-key] :as params} :params
                                    request-context :request-context
                                    headers :headers}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :read)
        (let [cache-key (keyword cache-key)
              cache (cache/context->cache context (keyword cache-name))
              result (cache/cache-lookup cache cache-key)]
          (when result
            {:status 200
             :body (json/generate-string result)}))))

    (POST "/clear-cache" {:keys [request-context params headers]}
      (let [context (acl/add-authentication-to-context request-context params headers)]
        (acl/verify-ingest-management-permission context :update)
        (cache/reset-caches context))
      {:status 200})))


;;; service proxies
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def json-header
  {"Content-Type" "json; charset=utf-8"})

(defn to-json
  "Converts the object to JSON. If the pretty parameter is passed with true formats the response for
  easy reading"
  [obj params]
  (json/generate-string obj {:pretty (= (get params :pretty) "true")}))

(defn- get-concept
  "Get a concept by concept-id and optional revision"
  ([context params concept-id]
   {:status 200
    :body (to-json (concept-service/get-concept context concept-id) params)
    :headers json-header})
  ([context params concept-id ^String revision]
   (try (let [revision-id (if revision (Integer. revision) nil)]
          {:status 200
           :body (to-json (concept-service/get-concept context concept-id revision-id) params)
           :headers json-header})
     (catch NumberFormatException e
       (serv-err/throw-service-error :invalid-data (.getMessage e))))))

(defn- get-concepts
  "Get concepts using concept-id/revision-id tuples."
  [context params concept-id-revisions]
  (let [allow-missing? (= "true" (some-> params :allow_missing str/lower-case))
        concepts (concept-service/get-concepts context concept-id-revisions allow-missing?)]
    {:status 200
     :body (to-json concepts params)
     :headers json-header}))

(defn- get-latest-concepts
  "Get latest version of concepts using a list of concept-ids"
  [context params concept-ids]
  (let [allow-missing? (= "true" (some-> params :allow_missing str/lower-case))
        concepts (concept-service/get-latest-concepts context concept-ids allow-missing?)]
    {:status 200
     :body (to-json concepts params)
     :headers json-header}))

(defn- get-expired-collections-concept-ids
  "Gets collections that have gone past their expiration date."
  [context params]
  (let [provider (:provider params)]
    (when-not provider
      (serv-err/throw-service-error :bad-request (msg/provider-id-parameter-required)))
    {:status 200
     :body (to-json (concept-service/get-expired-collections-concept-ids context provider) params)
     :headers json-header}))

(defn- find-concepts
  "Find concepts for a concept type with specific params"
  [context params]
  (let [params (update-in params [:concept-type] (comp keyword inf/singular))
        concepts (concept-service/find-concepts context params)]
    {:status 200
     :body (to-json concepts params)
     :headers json-header}))

(defn- save-concept
  "Store a concept record and return the revision"
  [context params concept]
  (let [concept (-> concept
                    clojure.walk/keywordize-keys
                    (update-in [:concept-type] keyword))
        {:keys [concept-id revision-id]} (concept-service/save-concept context concept)]
    {:status 201
     :body (to-json {:revision-id revision-id :concept-id concept-id} params)
     :headers json-header}))

(defn- delete-concept
  "Mark a concept as deleted (create a tombstone)."
  [context params concept-id revision-id]
  (try (let [revision-id (if revision-id (Integer. revision-id) nil)]
         (let [{:keys [revision-id]} (concept-service/delete-concept context concept-id revision-id)]
           {:status 200
            :body (to-json {:revision-id revision-id} params)
            :headers json-header}))
    (catch NumberFormatException e
      (serv-err/throw-service-error :invalid-data (.getMessage e)))))

(defn- force-delete
  "Permanently remove a concept version from the database."
  [context params concept-id revision-id]
  (try (let [revision-id (Integer. revision-id)]
         (let [{:keys [revision-id]} (concept-service/force-delete context concept-id revision-id)]
           {:status 200
            :body (to-json {:revision-id revision-id} params)
            :headers json-header}))
    (catch NumberFormatException e
      (serv-err/throw-service-error :invalid-data (.getMessage e)))))

(defn- reset
  "Delete all concepts from the data store"
  [context params]
  (concept-service/reset context)
  {:status 204})

(defn- get-concept-id
  "Get the concept id for a given concept."
  [context params concept-type provider-id native-id]
  (let [concept-id (concept-service/get-concept-id context concept-type provider-id native-id)]
    {:status 200
     :body (to-json {:concept-id concept-id} params)
     :headers json-header}))

(defn- save-provider
  "Save a provider."
  [context params provider-id]
  (let [saved-provider-id (provider-service/create-provider context provider-id)]
    {:status 201
     :body (to-json saved-provider-id params)
     :headers json-header}))

(defn- delete-provider
  "Delete a provider and all its concepts."
  [context params provider-id]
  (provider-service/delete-provider context provider-id)
  {:status 200})

(defn- get-providers
  "Get a list of provider ids"
  [context params]
  (let [providers (provider-service/get-providers context)]
    {:status 200
     :body (to-json {:providers providers} params)
     :headers json-header}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-routes [system]
  (routes
    (context (:relative-root-url system) []
      (context "/concepts" []

        (context "/search" []
          ;; get multiple concepts by concept-id and revision-id
          (POST "/concept-revisions" {:keys [params request-context body]}
            (get-concepts request-context params body))
          (POST "/latest-concept-revisions" {:keys [params request-context body]}
            (get-latest-concepts request-context params body))
          (GET "/expired-collections" {:keys [params request-context]}
            (get-expired-collections-concept-ids request-context params))
          ;; Find concepts by parameters
          (GET "/:concept-type" {:keys [params request-context]}
            (find-concepts request-context params)))

        ;; saves a concept
        (POST "/" {:keys [request-context params body]}
          (save-concept request-context params body))
        ;; mark a concept as deleted (add a tombstone) specifying the revision the tombstone should have
        (DELETE "/:concept-id/:revision-id" {{:keys [concept-id revision-id] :as params} :params
                                             request-context :request-context}
          (delete-concept request-context params concept-id revision-id))
        ;; mark a concept as deleted (add a tombstone)
        (DELETE "/:concept-id" {{:keys [concept-id] :as params} :params
                                request-context :request-context}
          (delete-concept request-context params concept-id nil))
        ;; remove a specific revision of a concept form the database
        (DELETE "/force-delete/:concept-id/:revision-id" {{:keys [concept-id revision-id] :as params} :params
                                                          request-context :request-context}
          (force-delete request-context params concept-id revision-id))
        ;; get a specific revision of a concept
        (GET "/:concept-id/:revision-id" {{:keys [concept-id revision-id] :as params} :params
                                          request-context :request-context}
          (get-concept request-context params concept-id revision-id))
        ;; get the latest revision of a concept
        (GET "/:concept-id" {{:keys [concept-id] :as params} :params request-context :request-context}
          (get-concept request-context params concept-id)))

      ;; get the concept id for a given concept-type, provider-id, and native-id
      (GET ["/concept-id/:concept-type/:provider-id/:native-id" :native-id #".*$"]
        {{:keys [concept-type provider-id native-id] :as params} :params request-context :request-context}
        (get-concept-id request-context params concept-type provider-id native-id))

      (context "/providers" []
        ;; create a new provider
        (POST "/" {:keys [request-context params body]}
          (save-provider request-context params (get body "provider-id")))
        ;; delete a provider
        (DELETE "/:provider-id" {{:keys [provider-id] :as params} :params request-context :request-context}
          (delete-provider request-context params provider-id))
        ;; get a list of providers
        (GET "/" {:keys [request-context params]}
          (get-providers request-context params)))

      ;; add routes for accessing caches
      cache-api-routes

      ;; delete the entire database
      (POST "/reset" {:keys [request-context params headers]}
        (let [context (acl/add-authentication-to-context request-context params headers)]
          (acl/verify-ingest-management-permission context :update)
          (cache/reset-caches request-context)
          (reset context params)))

      (context "/jobs" []
        ;; pause all jobs
        (POST "/pause" {:keys [request-context params headers]}
          (let [context (acl/add-authentication-to-context request-context params headers)]
            (acl/verify-ingest-management-permission context :update)
            (jobs/pause-jobs)))

        ;; resume all jobs
        (POST "/resume" {:keys [request-context params headers]}
          (let [context (acl/add-authentication-to-context request-context params headers)]
            (acl/verify-ingest-management-permission context :update)
            (jobs/resume-jobs))))

      (GET "/health" {request-context :request-context params :params}
        (let [{pretty? :pretty} params
              {:keys [ok? dependencies]} (hs/health request-context)]
          {:status (if ok? 200 503)
           :headers {"Content-Type" "application/json; charset=utf-8"}
           :body (json/generate-string dependencies {:pretty pretty?})})))

    (route/not-found "Not Found")))


(defn make-api [system]
  (-> (build-routes system)
      (http-trace/build-request-context-handler system)
      errors/exception-handler
      handler/site
      ring-json/wrap-json-body
      ring-json/wrap-json-response))





