(ns cmr.transmit.metadata-db
  "Provide functions to invoke metadata db app.
  DEPRECATED: The cmr.transmit.metadata-db2 namespace supersedes this one. We should move functions
  to that namespace using the latest style. Note when using the metadata db2 namespace functions that
  they do not throw exceptions except in exceptional cases like an invalid status code. A concept
  not being found is not considered an exceptional case."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [cmr.common.api.context :as ch]
   [cmr.common.log :refer (info warn)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.metadata-db2 :as mdb2]
   [ring.util.codec :as codec]
   [cmr.transmit.http-helper :as h]))

(declare get-concept context concept-id revision-id)
(defn-timed get-concept
  "Retrieve the concept with the given concept and revision-id"
  [context concept-id revision-id]
  (or (mdb2/get-concept context concept-id revision-id)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id "/" revision-id " from metadata-db."))))

(declare get-latest-concept context concept-id throw-service-error?)
(defn-timed get-latest-concept
  "Retrieve the latest version of the concept"
  ([context concept-id]
   (get-latest-concept context concept-id true))
  ([context concept-id throw-service-error?]
   (or (mdb2/get-latest-concept context concept-id)
       (when throw-service-error?
         (errors/throw-service-error
           :not-found
           (str "Failed to retrieve concept " concept-id " from metadata-db."))))))

(declare get-concept-id context concept-type provider-id native-id throw-service-error?)
(defn-timed get-concept-id
  "Return the concept-id for the concept matches the given arguments.
  By default, throw-service-error? is true and a 404 error is thrown if the concept is not found in
  metadata-db. It returns nil if the concept is not found and throw-service-error? is false."
  ([context concept-type provider-id native-id]
   (get-concept-id context concept-type provider-id native-id true))
  ([context concept-type provider-id native-id throw-service-error?]
   (let [conn (config/context->app-connection context :metadata-db)
         request-url (str (conn/root-url conn) "/concept-id/" (name concept-type) "/" provider-id
                          "/" (codec/url-encode native-id))
         params (merge
                 (config/conn-params conn)
                 {:accept :json
                  :headers (merge
                            (ch/context->http-headers context)
                            {:client-id config/cmr-client-id})
                  :throw-exceptions false
                  :http-options (h/include-request-id context {})})
         response (client/get request-url params)
         status (int (:status response))
         body (json/decode (:body response))]
     (case status
       404
       (when throw-service-error?
         (errors/throw-service-error
           :not-found
           (cmsg/invalid-native-id-msg concept-type provider-id native-id)))

       200
       (get body "concept-id")

       ;; default
       (let [errors-str (json/generate-string (flatten (get body "errors")))
             err-msg (str "Concept id fetch failed. MetadataDb app response status code: "  status)]
         (errors/internal-error! (str err-msg  " " errors-str)))))))

(declare get-concept-revisions context concept-tuples allow-missing?)
(defn-timed get-concept-revisions
  "Search metadata db and return the concepts given by the concept-id, revision-id tuples."
  ([context concept-tuples]
   (get-concept-revisions context concept-tuples false))
  ([context concept-tuples allow-missing?]
   (let [conn (config/context->app-connection context :metadata-db)
         tuples-json-str (json/generate-string concept-tuples)
         request-url (str (conn/root-url conn) "/concepts/search/concept-revisions")
         params (merge
                 (config/conn-params conn)
                 {:body tuples-json-str
                  :content-type :json
                  :query-params {:allow_missing allow-missing?}
                  :accept :json
                  :throw-exceptions false
                  :headers (merge
                            (ch/context->http-headers context)
                            {:client-id config/cmr-client-id})
                  :http-options (h/include-request-id context {})})
         response (client/post request-url params)
         status (int (:status response))]
     (case status
       404
       (let [err-msg "Unable to find all concepts."]
         (info "Not found response body:" (:body response))
         (errors/throw-service-error :not-found err-msg))

       200
       (map mdb2/finish-parse-concept (json/decode (:body response) true))

       ;; default
       (errors/internal-error!
        (str "Get concept revisions failed. MetadataDb app response status code: "
             status
             " "
             response))))))

(declare get-latest-concepts context concept-ids allow-missing?)
(defn-timed get-latest-concepts
  "Search metadata db and return the latest-concepts given by the concept-id list"
  ([context concept-ids]
   (get-latest-concepts context concept-ids false))
  ([context concept-ids allow-missing?]
   (let [conn (config/context->app-connection context :metadata-db)
         ids-json-str (json/generate-string concept-ids)
         request-url (str (conn/root-url conn) "/concepts/search/latest-concept-revisions")
         params (merge
                 (config/conn-params conn)
                 {:body ids-json-str
                  :query-params {:allow_missing allow-missing?}
                  :content-type :json
                  :accept :json
                  :throw-exceptions false
                  :headers (merge
                            (ch/context->http-headers context)
                            {:client-id config/cmr-client-id})
                  :http-options (h/include-request-id context {})})
         response (client/post request-url params)
         status (int (:status response))]
     (case status
       404
       (let [err-msg "Unable to find all concepts."]
         (info "Not found response body:" (:body response))
         (errors/throw-service-error :not-found err-msg))

       200
       (map mdb2/finish-parse-concept (json/decode (:body response) true))

       ;; default
       (errors/internal-error! (str "Get latest concept revisions failed. MetadataDb app response status code: "
                                    status
                                    " "
                                    response))))))

(defn- find-concepts-raw
  "Searches metadata db for concepts matching the given parameters, returns the raw response from
  the http get."
  [context params concept-type]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) (format "/concepts/search/%ss" (name concept-type)))
        params (merge
                (config/conn-params conn)
                {:accept :json
                 :form-params params
                 :headers (merge
                           (ch/context->http-headers context)
                           {:client-id config/cmr-client-id})
                 :throw-exceptions false
                 :http-options (h/include-request-id context {})})]
    (client/post request-url params)))

(defn find-concepts
  "Searches metadata db for concepts matching the given parameters."
  [context params concept-type]
  (let [{:keys [status body]} (find-concepts-raw context params concept-type)
        status (int status)]
    (case status
      200 (map mdb2/finish-parse-concept (json/decode body true))
      ;; default
      (errors/internal-error!
        (format "%s search failed. status: %s body: %s"
                (string/capitalize (name concept-type)) status body)))))

(defn find-latest-concept
  "Searches metadata db for the latest concept matching the given parameters. Do not throw serivce
  exception, returns the status and error message in a map in case of error."
  [context params concept-type]
  (let [{:keys [status body]} (find-concepts-raw context (assoc params :latest true) concept-type)
        status (int status)]
    (case status
      200 (first (map mdb2/finish-parse-concept (json/decode body true)))
      404 (errors/throw-service-error :not-found body)
      ;; default
      (errors/internal-error!
        (format "%s search failed. status: %s body: %s"
                (string/capitalize (name concept-type)) status body)))))

(defn get-associations-by-collection-concept-id
  "Get all the associations of the given type (including tombstones) for a collection
  with the given concept id and revision id. assoc-type can be :tag-association,
  :variable-association or service-association."
  [context coll-concept-id coll-revision-id assoc-type]
  (let [params {:associated-concept-id coll-concept-id
                :latest true}
        associations (find-concepts context params assoc-type)]
    ;; we only want the associations that have no associated revision id or one equal to the
    ;; revision of this collection
    (filter (fn [ta] (let [rev-id (get-in ta [:extra-fields :associated-revision-id])]
                       (or (nil? rev-id)
                           (= rev-id coll-revision-id))))
            associations)))

(defn get-associations-for-collection
  "Get all the associations of the given type (including tombstones) for a collection.
   assoc-type can be either :tag-association or :variable-association."
  [context concept assoc-type]
  (get-associations-by-collection-concept-id
   context (:concept-id concept) (:revision-id concept) assoc-type))

(defn get-associations-for-variable
  "Get variable associations (including tombstones) for a given variable."
  [context concept]
  (let [params {:variable-concept-id (:concept-id concept)
                :latest true}]
    (find-concepts context params :variable-association)))

(defn get-associations-for-service
  "Get service associations (including tombstones) for a given service."
  [context concept]
  (let [params {:service-concept-id (:concept-id concept)
                :latest true}]
    (find-concepts context params :service-association)))

(defn get-associations-for-tool
  "Get tool associations (including tombstones) for a given tool."
  [context concept]
  (let [params {:tool-concept-id (:concept-id concept)
                :latest true}]
    (find-concepts context params :tool-association)))

(defn get-generic-associations-by-concept-id
  "Get generic associations (including tombstones) for a given concept-id revision-id."
  [context concept-id revision-id]
  (let [;;params used to get associations with source-concept-identifier being the generic concept
        source-params {:source-concept-identifier concept-id
                       :latest true}
        associations-source (find-concepts context source-params :generic-association)
        ;;we only want the associations that have no associated revision id or one equal to the
        ;; revision
        associations-source (filter (fn [ga] (let [rev-id (get-in ga [:extra-fields :source-revision-id])]
                                               (or (nil? rev-id)
                                                   (= rev-id revision-id))))
                                    associations-source)
        ;;params used to get associations with associated-concept-id being the generic concept
        dest-params {:associated-concept-id concept-id
                     :latest true}
        associations-dest (find-concepts context dest-params :generic-association)
        associations-dest (filter (fn [ga] (let [rev-id (get-in ga [:extra-fields :associated-revision-id])]
                                             (or (nil? rev-id)
                                                 (= rev-id revision-id))))
                                  associations-dest)]
    (concat associations-source associations-dest)))

(defn get-generic-associations-for-concept
  "Get all the generic associations (including tombstones) for a concept."
  [context concept]
  (get-generic-associations-by-concept-id
   context (:concept-id concept) (:revision-id concept)))

(defn- find-associations-raw
  "Searches metadata db for associations matching the given parameters.
  Returns the raw response."
  [context params]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/associations/search")
        params (merge
                (config/conn-params conn)
                {:accept :json
                 :form-params params
                 :headers (merge
                           (ch/context->http-headers context)
                           {:client-id config/cmr-client-id})
                 :throw-exceptions false
                 :http-options (h/include-request-id context {})})]
    (client/post request-url params)))

(defn find-associations
  "Searches metadata db for associations matching the given parameters.
  Keeps the associations that do not have a revision id (latest), or matches
  the passed in revision id."
  [context concept]
  (let [concept-id (:concept-id concept)
        revision-id (:revision-id concept)
        params {:associated-concept-id concept-id
                :source-concept-identifier concept-id
                :latest true}
        {:keys [status body]} (find-associations-raw context params)
        status (int status)]
    (case status
      200 (let [associations (json/decode body true)]
            (filter (fn [association]
                      (let [associated-concept-id (get-in association [:extra-fields :associated-concept-id])]
                        (if (= concept-id associated-concept-id)
                          (let [rev-id (get-in association [:extra-fields :associated-revision-id])]
                            (or (nil? rev-id)
                                (= rev-id revision-id)))
                          (let [rev-id (get-in association [:extra-fields :source-revision-id])]
                            (or (nil? rev-id)
                                (= rev-id revision-id))))))
                    associations))
      ;; default
      (errors/internal-error!
       (format "association search failed. status: %s body: %s" status body)))))

(declare find-collections params)
(defn-timed find-collections
  "Searches metadata db for concepts matching the given parameters."
  [context params]
  (find-concepts context params :collection))

(defn find-in-batches
  "Searches metadata db for collection/tag revisions matching the given parameters and pulls them back
  with metadata in batches in order to save memory. It does this by first doing a search excluding
  metadata. Then it lazily pulls back batches of those with metadata. This assumes every concept
  found can fit into memory without the metadata."
  [context concept-type batch-size params]
  (info (format "Fetching batches of [%d] [%s] from metadata-db: %s"
                batch-size
                (name concept-type)
                (str params)))
  (->> (find-concepts context (assoc params :exclude-metadata true) concept-type)
       (map #(vector (:concept-id %) (:revision-id %)))
       (partition-all batch-size)
       ;; It's important this step is done lazily.
       (map #(get-concept-revisions context %))))

(declare get-expired-collection-concept-ids)
(defn-timed get-expired-collection-concept-ids
  "Searches metadata db for collections in a provider that have expired and returns their concept ids."
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/concepts/search/expired-collections")
        params (merge
                (config/conn-params conn)
                {:accept :json
                 :query-params {:provider provider-id}
                 :headers (merge
                           (ch/context->http-headers context)
                           {:client-id config/cmr-client-id})
                 :throw-exceptions false
                 :http-options (h/include-request-id context {})})
        response (client/get request-url params)
        {:keys [status body]} response
        status (int status)]
    (case status
      200 (json/decode body true)
      ;; default
      (errors/internal-error!
        (format "Collection search failed. status: %s body: %s"
                status body)))))

(defn create-provider-raw
  "Create the provider with the given provider id, returns the raw response coming back from metadata-db"
  [context provider]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers")
        params {:body (json/generate-string provider)
                :content-type :json
                :headers {config/token-header (config/echo-system-token)
                          :client-id config/cmr-client-id}
                :throw-exceptions false
                :http-options (h/include-request-id context {})}]
    (client/post request-url params)))

(declare create-provider provider)
(defn-timed create-provider
  "Create the provider with the given provider id"
  [context provider]
  (let [{:keys [status body]} (create-provider-raw context provider)]
    (when-not (= status 201)
      (errors/internal-error!
        (format "Failed to create provider status: %s body: %s"
                status body)))))

(declare read-provider)
(defn-timed read-provider
  "Reads a provider with the given provider id"
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers/" provider-id)
        params {:headers {config/token-header (config/echo-system-token)
                          :client-id config/cmr-client-id}
                :throw-exceptions false
                :http-options (h/include-request-id context {})}]
    (client/get request-url params)))

(declare read-providers)
(defn-timed read-providers
  "Reads all providers"
  [context]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers")
        params {:headers {config/token-header (config/echo-system-token)
                          :client-id config/cmr-client-id}
                :throw-exceptions false
                :http-options (h/include-request-id context {})}]
    (client/get request-url params)))

(defn update-provider-raw
  "Update a provider in the database"
  [context provider]
  (let [conn (config/context->app-connection context :metadata-db)
        provider-id (get provider :ProviderId (get provider :provider-id))
        request-url (str (conn/root-url conn) "/providers/" provider-id)
        params {:body (json/generate-string provider)
                :content-type :json
                :headers {config/token-header (config/echo-system-token)
                          :client-id config/cmr-client-id}
                :throw-exceptions false
                :http-options (h/include-request-id context {})}]
    (client/put request-url params)))

(defn delete-provider-raw
  "Delete the provider with the matching provider-id from the CMR metadata repo,
  returns the raw response coming back from metadata-db."
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers/" provider-id)
        params {:throw-exceptions false
                :headers {config/token-header (config/echo-system-token)
                          :client-id config/cmr-client-id}
                :http-options (h/include-request-id context {})}]
    (client/delete request-url params)))

(declare delete-provider)
(defn-timed delete-provider
  "Delete the provider with the matching provider-id from the CMR metadata repo."
  [context provider-id]
  (let [{:keys [status body]} (delete-provider-raw context provider-id)]
    (when-not (or (> 300 status 199) (= status 404))
      (errors/internal-error!
        (format "Failed to delete provider status: %s body: %s"
                status body)))))

(defn get-providers-raw
  "Returns the list of provider ids configured in the metadata db,
  returns the raw response coming back from metadata-db"
  [context]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers")
        params (merge
                (config/conn-params conn)
                {:accept :json
                 :headers (merge
                           (ch/context->http-headers context)
                           {:client-id config/cmr-client-id})
                 :throw-exceptions false
                 :http-options (h/include-request-id context {})})]
    (client/get request-url params)))

(defn get-all-providers
  "Returns the list of provider ids configured in the metadata db, including
  the metadata for each given provider by passing meta argument to metadatadb app"
  [context]
  (let [request-url (fn [conn] (str (conn/root-url conn) "/providers"))]
    ;; Using http/helper pass "meta" arg to retrieve provider metadata
    (h/request context :metadata-db {:url-fn request-url
                                     :method :get
                                     :headers {:client-id config/cmr-client-id}
                                     :http-options (h/include-request-id
                                                    context
                                                    {:query-params {:meta true}})})))

(declare get-providers)
(defn-timed get-providers
  "Returns the list of provider ids configured in the metadata db"
  [context]
  (let [{:keys [status body]} (get-providers-raw context)
        status (int status)]
    (case status
      200 (json/decode body true)
      ;; default
      (errors/internal-error! (format "Failed to get providers status: %s body: %s" status body)))))

(declare save-concept concept)
(defn-timed save-concept
  "Saves a concept in metadata db"
  [context concept]
  (let [conn (config/context->app-connection context :metadata-db)
        url (str (conn/root-url conn) "/concepts")
        concept-json-str (json/generate-string concept)
        params (merge
                (config/conn-params conn)
                {:body concept-json-str
                 :content-type :json
                 :accept :json
                 :throw-exceptions false
                 :headers (merge
                           (ch/context->http-headers context)
                           {:client-id config/cmr-client-id})
                 :http-options (h/include-request-id context {})})
        response (client/post url params)
        status (int (:status response))
        ;; For CMR-4841 - log the first 255 characters of the response body if
        ;; the parsing of the html throws exception.
        response-body (:body response)
        body (try
               (json/decode response-body)
               (catch Exception e
                 (warn "Exception occurred while parsing the response body: "
                       (util/trunc response-body 255))
                 (throw e)))
        {:strs [concept-id revision-id]} body]
    (case status
      422
      (errors/throw-service-errors :invalid-data (get body "errors"))

      201
      {:concept-id concept-id :revision-id revision-id}

      409
      ;; Post commit constraint violation occurred
      (errors/throw-service-errors :conflict (get body "errors"))

      ;; default
      (errors/internal-error!
       (format "Save concept failed. MetadataDb app response status code: %s response: %s"
               status response)))))

(declare delete-draft)
(defn-timed delete-draft
  "delete a draft in metadata db"
  [context concept]
  (let [conn (config/context->app-connection context :metadata-db)
        url (str (conn/root-url conn) "/concepts/force-delete-draft/" (:concept-id concept))
        concept-json-str (json/generate-string concept)
        params (merge
                (config/conn-params conn)
                {:body concept-json-str
                 :content-type :json
                 :accept :json
                 :throw-exceptions false
                 :headers (merge
                           (ch/context->http-headers context)
                           {:client-id config/cmr-client-id})
                 :http-options (h/include-request-id context {})})
        response (client/delete url params)
        status (int (:status response))
        ;; For CMR-4841 - log the first 255 characters of the response body if
        ;; the parsing of the html throws exception.
        response-body (:body response)
        body (try
               (json/decode response-body)
               (catch Exception e
                 (warn "Exception occurred while parsing the response body: "
                       (util/trunc response-body 255))
                 (throw e)))
        {:strs [concept-id]} body]
    (case status
      422
      (errors/throw-service-errors :invalid-data (get body "errors"))

      200
      {:concept-id concept-id}

      409
      ;; Post commit constraint violation occurred
      (errors/throw-service-errors :conflict (get body "errors"))

      ;; default
      (errors/internal-error!
       (format "Delete draft failed. MetadataDb app response status code: %s response: %s"
               status response)))))
