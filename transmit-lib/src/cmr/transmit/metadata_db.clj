(ns cmr.transmit.metadata-db
  "Provide functions to invoke metadata db app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.health-helper :as hh]
            [cmr.transmit.config :as config]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [cmr.system-trace.http :as ch]
            [ring.util.codec :as codec]
            [cmr.acl.core :as acl]
            [cmr.transmit.connection :as conn]
            [cmr.common.log :refer (debug info warn error)]))

(defn get-concept
  "Retrieve the concept with the given concept and revision-id"
  [context concept-id revision-id]
  (let [conn (config/context->app-connection context :metadata-db)
        response (client/get (format "%s/concepts/%s/%s" (conn/root-url conn) concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false
                              :headers (ch/context->http-headers context)
                              :connection-manager (conn/conn-mgr conn)})]
    (if (= 200 (:status response))
      (cheshire/decode (:body response) true)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id "/" revision-id " from metadata-db: " (:body response))))))

(defn get-latest-concept
  "Retrieve the latest version of the concept"
  ([context concept-id]
   (get-latest-concept context concept-id true))
  ([context concept-id throw-service-error?]
   (let [conn (config/context->app-connection context :metadata-db)
         response (client/get (format "%s/concepts/%s" (conn/root-url conn) concept-id)
                              {:accept :json
                               :throw-exceptions false
                               :headers (ch/context->http-headers context)
                               :connection-manager (conn/conn-mgr conn)})
         status (:status response)]
     (if (= 200 status)
       (cheshire/parse-string (:body response) true)
       (when (and throw-service-error? (= 404 status))
         (errors/throw-service-error
           :not-found
           (str "Failed to retrieve concept " concept-id " from metadata-db: " (:body response))))))))

(defn get-concept-id
  "Return a distinct identifier for the given arguments."
  [context concept-type provider-id native-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/concept-id/" (name concept-type) "/" provider-id "/"
                         (codec/url-encode native-id))
        response (client/get request-url {:accept :json
                                          :headers (ch/context->http-headers context)
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        status (:status response)
        body (cheshire/decode (:body response))]
    (case status
      404
      (let [err-msg (format "concept-type: %s provider-id: %s native-id: %s does not exist" concept-type provider-id native-id)]
        (errors/throw-service-error :not-found err-msg))

      200
      (get body "concept-id")

      ;; default
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))
            err-msg (str "Concept id fetch failed. MetadataDb app response status code: "  status)]
        (errors/internal-error! (str err-msg  " " errors-str))))))

(defn get-concept-revisions
  "Search metadata db and return the concepts given by the concept-id, revision-id tuples."
  ([context concept-tuples]
   (get-concept-revisions context concept-tuples false))
  ([context concept-tuples allow-missing?]
   (let [conn (config/context->app-connection context :metadata-db)
         tuples-json-str (cheshire/generate-string concept-tuples)
         request-url (str (conn/root-url conn) "/concepts/search/concept-revisions")
         response (client/post request-url {:body tuples-json-str
                                            :content-type :json
                                            :query-params {:allow_missing allow-missing?}
                                            :accept :json
                                            :throw-exceptions false
                                            :headers (ch/context->http-headers context)
                                            :connection-manager (conn/conn-mgr conn)})
         status (:status response)]
     (case status
       404
       (let [err-msg "Unable to find all concepts."]
         (debug "Not found response body:" (:body response))
         (errors/throw-service-error :not-found err-msg))

       200
       (cheshire/decode (:body response) true)

       ;; default
       (errors/internal-error! (str "Get concept revisions failed. MetadataDb app response status code: "
                                    status
                                    " "
                                    response))))))

(defn get-latest-concepts
  "Search metadata db and return the latest-concepts given by the concept-id list"
  ([context concept-ids]
   (get-latest-concepts context concept-ids false))
  ([context concept-ids allow-missing?]
   (let [conn (config/context->app-connection context :metadata-db)
         ids-json-str (cheshire/generate-string concept-ids)
         request-url (str (conn/root-url conn) "/concepts/search/latest-concept-revisions")
         response (client/post request-url {:body ids-json-str
                                            :query-params {:allow_missing allow-missing?}
                                            :content-type :json
                                            :accept :json
                                            :throw-exceptions false
                                            :headers (ch/context->http-headers context)
                                            :connection-manager (conn/conn-mgr conn)})
         status (:status response)]
     (case status
       404
       (let [err-msg "Unable to find all concepts."]
         (debug "Not found response body:" (:body response))
         (errors/throw-service-error :not-found err-msg))

       200
       (cheshire/decode (:body response) true)

       ;; default
       (errors/internal-error! (str "Get latest concept revisions failed. MetadataDb app response status code: "
                                    status
                                    " "
                                    response))))))

(defn find-collections
  "Searches metadata db for concepts matching the given parameters."
  [context params]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/concepts/search/collections")
        response (client/get request-url {:accept :json
                                          :query-params params
                                          :headers (ch/context->http-headers context)
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response]
    (case status
      404 (errors/throw-service-error
            :not-found (str "Unable to find collections for search params: " (pr-str params)))
      200 (cheshire/decode body true)
      ;; default
      (errors/internal-error!
        (format "Collection search failed. status: %s body: %s"
                status body)))))

(defn get-expired-collection-concept-ids
  "Searches metadata db for collections in a provider that have expired and returns their concept ids."
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/concepts/search/expired-collections")
        response (client/get request-url {:accept :json
                                          :query-params {:provider provider-id}
                                          :headers (ch/context->http-headers context)
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response]
    (case status
      200 (cheshire/decode body true)
      ;; default
      (errors/internal-error!
        (format "Collection search failed. status: %s body: %s"
                status body)))))

(defn create-provider-raw
  "Create the provider with the given provider id, returns the raw response coming back from metadata-db"
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers")]
    (client/post request-url
                 {:body (format "{\"provider-id\": \"%s\"}" provider-id)
                  :content-type :json
                  :headers {acl/token-header (config/echo-system-token)}
                  :throw-exceptions false})))

(defn create-provider
  "Create the provider with the given provider id"
  [context provider-id]
  (let [{:keys [status body]} (create-provider-raw context provider-id)]
    (when-not (= status 201)
      (errors/internal-error!
        (format "Failed to create provider status: %s body: %s"
                status body)))))

(defn delete-provider-raw
  "Delete the provider with the matching provider-id from the CMR metadata repo,
  returns the raw response coming back from metadata-db."
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers/" provider-id)]
    (client/delete request-url {:throw-exceptions false
                                :headers {acl/token-header (config/echo-system-token)}})))

(defn delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [context provider-id]
  (let [{:keys [status body]} (delete-provider-raw context provider-id)]
    (when-not (or (= status 200) (= status 404))
      (errors/internal-error!
        (format "Failed to delete provider status: %s body: %s"
                status body)))))

(defn get-providers-raw
  "Returns the list of provider ids configured in the metadata db,
  returns the raw response coming back from metadata-db"
  [context]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers")]
    (client/get request-url {:accept :json
                             :headers (ch/context->http-headers context)
                             :throw-exceptions false
                             :connection-manager (conn/conn-mgr conn)})))

(defn get-providers
  "Returns the list of provider ids configured in the metadata db"
  [context]
  (let [{:keys [status body]} (get-providers-raw context)]
    (case status
      200 (-> body (cheshire/decode true) :providers)
      ;; default
      (errors/internal-error! (format "Failed to get providers status: %s body: %s" status body)))))

(defn save-concept
  "Saves a concept in metadata db and index."
  [context concept]
  (let [conn (config/context->app-connection context :metadata-db)
        concept-json-str (cheshire/generate-string concept)
        response (client/post (str (conn/root-url conn) "/concepts")
                              {:body concept-json-str
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :headers (ch/context->http-headers context)
                               :connection-manager (conn/conn-mgr conn)})
        status (:status response)
        body (cheshire/decode (:body response))
        {:strs [concept-id revision-id]} body]
    (case status
      422
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))]
        ;; catalog rest supplied invalid concept id
        (errors/throw-service-error :invalid-data errors-str))

      201
      {:concept-id concept-id :revision-id revision-id}

      ;; default
      (errors/internal-error! (str "Save concept failed. MetadataDb app response status code: "
                                   status
                                   " "
                                   response)))))

(defn delete-concept
  "Delete a concept from metatdata db."
  [context concept-id]
  (let [conn (config/context->app-connection context :metadata-db)
        response (client/delete (str (conn/root-url conn) "/concepts/" concept-id)
                                {:accept :json
                                 :throw-exceptions false
                                 :headers (ch/context->http-headers context)
                                 :connection-manager (conn/conn-mgr conn)})
        status (:status response)
        body (cheshire/decode (:body response))]
    (case status
      404
      (let [errors-str (cheshire/generate-string (flatten (get body "errors")))]
        (errors/throw-service-error :not-found errors-str))

      200
      (get body "revision-id")

      ;; default
      (errors/internal-error! (str "Delete concept operation failed. MetadataDb app response status code: "
                                   status
                                   " "
                                   response)))))

(defn get-metadata-db-health-fn
  "Returns the health status of the metadata db"
  [context]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/health")
        response (client/get request-url {:accept :json
                                          :throw-exceptions false
                                          :connection-manager (conn/conn-mgr conn)})
        {:keys [status body]} response
        result (cheshire/decode body true)]
    (if (= 200 status)
      {:ok? true :dependencies result}
      {:ok? false :problem result})))

(defn get-metadata-db-health
  "Returns the metadata-db health with timeout handling."
  [context]
  (let [timeout-ms (* 1000 (+ 2 (hh/health-check-timeout-seconds)))]
    (hh/get-health #(get-metadata-db-health-fn context) timeout-ms)))
