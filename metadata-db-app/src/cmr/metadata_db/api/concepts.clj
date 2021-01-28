(ns cmr.metadata-db.api.concepts
  "Defines the HTTP URL routes that deal with concepts."
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.metadata-db.api.route-helpers :as rh]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.search-service :as search-service]
   [compojure.core :refer :all]
   [inflections.core :as inf]))

(defn as-int
  "Parses the string to return an integer"
  [^String v]
  (try
    (when v (Integer. v))
    (catch NumberFormatException e
      (errors/throw-service-error :invalid-data (.getMessage e)))))

(defn- get-concept
  "Get a concept by concept-id and optional revision"
  ([context params concept-id]
   (get-concept context params concept-id nil))
  ([context params concept-id revision]
   {:status 200
    :body (json/generate-string (concept-service/get-concept context concept-id (as-int revision)))
    :headers rh/json-header}))

(defn- allow-missing?
  "Returns true if the allow_missing parameter is set to true"
  [params]
  (= "true" (some-> params :allow_missing str/lower-case)))

(defn- get-concepts
  "Get concepts using concept-id/revision-id tuples."
  [context params concept-id-revisions]
  (let [concepts (concept-service/get-concepts context concept-id-revisions (allow-missing? params))]
    {:status 200
     :body (json/generate-string concepts)
     :headers rh/json-header}))

(defn- get-latest-concepts
  "Get latest version of concepts using a list of concept-ids"
  [context params concept-ids]
  (let [concepts (concept-service/get-latest-concepts context concept-ids (allow-missing? params))]
    {:status 200
     :body (json/generate-string concepts)
     :headers rh/json-header}))

(defn- get-expired-collections-concept-ids
  "Gets collections that have gone past their expiration date."
  [context params]
  (if-let [provider (:provider params)]
    {:status 200
     :body (json/generate-string (concept-service/get-expired-collections-concept-ids context provider))
     :headers rh/json-header}
    (errors/throw-service-error :bad-request (msg/provider-id-parameter-required))))

(defn- find-concepts
  "Find concepts for a concept type with specific params"
  [context params]
  (let [params (update-in params [:concept-type] (comp keyword inf/singular))
        concepts (search-service/find-concepts context params)]
    {:status 200
     :body (json/generate-string concepts)
     :headers rh/json-header}))

(defn- save-concept-revision
  "Store a concept record and return the revision"
  [context params concept]
  (let [concept (-> concept
                    clojure.walk/keywordize-keys
                    (update-in [:concept-type] keyword))
        ;; get variable-association and associated-item when applicable.
        {:keys [concept-id revision-id variable-association associated-item]}
        (concept-service/save-concept-revision context concept)]
    {:status 201
     :body (json/generate-string (util/remove-nil-keys
                                  {:revision-id revision-id
                                   :concept-id concept-id
                                   :variable-association variable-association
                                   :associated-item associated-item}))
     :headers rh/json-header}))

(defn- delete-concept
  "Mark a concept as deleted (create a tombstone)."
  [context params concept-id revision-id]
  (let [{:keys [revision-id]} (concept-service/save-concept-revision
                                context {:concept-id concept-id
                                         :revision-id (as-int revision-id)
                                         :revision-date (:revision-date params)
                                         :deleted true})]
    {:status 201
     :body (json/generate-string {:revision-id revision-id})
     :headers rh/json-header}))

(defn- force-delete
  "Permanently remove a concept version from the database."
  [context params concept-id revision-id]
  (let [;; force? is only used for testing purpose, should not be used in real operations
        force? (= "true" (:force params))
        {:keys [revision-id]} (concept-service/force-delete
                                context concept-id (as-int revision-id) force?)]
    {:status 200
     :body (json/generate-string {:revision-id revision-id})
     :headers rh/json-header}))

(defn- get-concept-id
  "Get the concept id for a given concept."
  [context params concept-type provider-id native-id]
  (let [concept-id (concept-service/get-concept-id context concept-type provider-id native-id)]
    {:status 200
     :body (json/generate-string {:concept-id concept-id})
     :headers rh/json-header}))

(defn- get-provider-holdings
  "Returns the provider holdings within metadata db"
  [context params]
  {:status 200
   :body (json/generate-string (concept-service/get-provider-holdings context))
   :headers rh/json-header})

(def concepts-api-routes
  (routes
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
          (find-concepts request-context params))
        (POST "/:concept-type" {:keys [params request-context]}
          (find-concepts request-context params)))
      ;; saves a concept
      (POST "/" {:keys [request-context params body]}
        (save-concept-revision request-context params body))
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
      (get-concept-id request-context params (keyword concept-type) provider-id native-id))

    (GET "/provider_holdings" {context :request-context params :params}
      (get-provider-holdings context params))))
