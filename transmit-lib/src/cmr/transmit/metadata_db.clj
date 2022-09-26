(ns cmr.transmit.metadata-db
  "Provide functions to invoke metadata db app.
  DEPRECATED: The cmr.transmit.metadata-db2 namespace supersedes this one. We should move functions
  to that namespace using the latest style. Note when using the metadata db2 namespace functions that
  they do not throw exceptions except in exceptional cases like an invalid status code. A concept
  not being found is not considered an exceptional case."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [cmr.common.api.context :as ch]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.util :as util :refer [defn-timed]]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.metadata-db2 :as mdb2]
   [ring.util.codec :as codec]))

(defn-timed get-concept
  "Retrieve the concept with the given concept and revision-id"
  [context concept-id revision-id]
  (or (mdb2/get-concept context concept-id revision-id)
      (errors/throw-service-error
        :not-found
        (str "Failed to retrieve concept " concept-id "/" revision-id " from metadata-db."))))

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

(defn-timed get-concept-id
  "Return the concept-id for the concept matches the given arguments.
  By default, throw-service-error? is true and a 404 error is thrown if the concept is not found in
  metadata-db. It returns nil if the concept is not found and throw-service-error? is false."
  ([context concept-type provider-id native-id]
   (get-concept-id context concept-type provider-id native-id true))
  ([context concept-type provider-id native-id throw-service-error?]
   (let [conn (config/context->app-connection context :metadata-db)
         request-url (str (conn/root-url conn) "/concept-id/" (name concept-type) "/" provider-id "/"
                          (codec/url-encode native-id))
         response (client/get request-url (merge
                                            (config/conn-params conn)
                                            {:accept :json
                                             :headers (ch/context->http-headers context)
                                             :throw-exceptions false}))
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

(defn-timed get-concept-revisions
  "Search metadata db and return the concepts given by the concept-id, revision-id tuples."
  ([context concept-tuples]
   (get-concept-revisions context concept-tuples false))
  ([context concept-tuples allow-missing?]
   (let [conn (config/context->app-connection context :metadata-db)
         tuples-json-str (json/generate-string concept-tuples)
         request-url (str (conn/root-url conn) "/concepts/search/concept-revisions")
         response (client/post request-url (merge
                                             (config/conn-params conn)
                                             {:body tuples-json-str
                                              :content-type :json
                                              :query-params {:allow_missing allow-missing?}
                                              :accept :json
                                              :throw-exceptions false
                                              :headers (ch/context->http-headers context)}))
         status (int (:status response))]
     (case status
       404
       (let [err-msg "Unable to find all concepts."]
         (info "Not found response body:" (:body response))
         (errors/throw-service-error :not-found err-msg))

       200
       (map mdb2/finish-parse-concept (json/decode (:body response) true))

       ;; default
       (errors/internal-error! (str "Get concept revisions failed. MetadataDb app response status code: "
                                    status
                                    " "
                                    response))))))

(defn-timed get-latest-concepts
  "Search metadata db and return the latest-concepts given by the concept-id list"
  ([context concept-ids]
   (get-latest-concepts context concept-ids false))
  ([context concept-ids allow-missing?]
   (let [conn (config/context->app-connection context :metadata-db)
         ids-json-str (json/generate-string concept-ids)
         request-url (str (conn/root-url conn) "/concepts/search/latest-concept-revisions")
         response (client/post request-url (merge
                                             (config/conn-params conn)
                                             {:body ids-json-str
                                              :query-params {:allow_missing allow-missing?}
                                              :content-type :json
                                              :accept :json
                                              :throw-exceptions false
                                              :headers (ch/context->http-headers context)}))
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
        request-url (str (conn/root-url conn) (format "/concepts/search/%ss" (name concept-type)))]
    (client/post request-url (merge
                              (config/conn-params conn)
                              {:accept :json
                               :form-params params
                               :headers (ch/context->http-headers context)
                               :throw-exceptions false}))))

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
                (str/capitalize (name concept-type)) status body)))))

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
                (str/capitalize (name concept-type)) status body)))))

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
  "Get all the generic associationse (including tombstones) for a concept."
  [context concept]
  (get-generic-associations-by-concept-id
   context (:concept-id concept) (:revision-id concept)))

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

(defn-timed get-expired-collection-concept-ids
  "Searches metadata db for collections in a provider that have expired and returns their concept ids."
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/concepts/search/expired-collections")
        response (client/get request-url (merge
                                           (config/conn-params conn)
                                           {:accept :json
                                            :query-params {:provider provider-id}
                                            :headers (ch/context->http-headers context)
                                            :throw-exceptions false}))
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
        request-url (str (conn/root-url conn) "/providers")]
    (client/post request-url
                 {:body (json/generate-string provider)
                  :content-type :json
                  :headers {config/token-header (config/echo-system-token)}
                  :throw-exceptions false})))

(defn-timed create-provider
  "Create the provider with the given provider id"
  [context provider]
  (let [{:keys [status body]} (create-provider-raw context provider)]
    (when-not (= status 201)
      (errors/internal-error!
        (format "Failed to create provider status: %s body: %s"
                status body)))))

(defn update-provider-raw
  [context {:keys [provider-id] :as provider}]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers/" provider-id)]
    (client/put request-url
                {:body (json/generate-string provider)
                 :content-type :json
                 :headers {config/token-header (config/echo-system-token)}
                 :throw-exceptions false})))

(defn delete-provider-raw
  "Delete the provider with the matching provider-id from the CMR metadata repo,
  returns the raw response coming back from metadata-db."
  [context provider-id]
  (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/providers/" provider-id)]
    (client/delete request-url {:throw-exceptions false
                                :headers {config/token-header (config/echo-system-token)}})))

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
        request-url (str (conn/root-url conn) "/providers")]
    (client/get request-url (merge
                              (config/conn-params conn)
                              {:accept :json
                               :headers (ch/context->http-headers context)
                               :throw-exceptions false}))))

(defn-timed get-providers
  "Returns the list of provider ids configured in the metadata db"
  [context]
  (let [{:keys [status body]} (get-providers-raw context)
        status (int status)]
    (case status
      200 (json/decode body true)
      ;; default
      (errors/internal-error! (format "Failed to get providers status: %s body: %s" status body)))))

(defn-timed save-concept
  "Saves a concept in metadata db"
  [context concept]
  (let [conn (config/context->app-connection context :metadata-db)
        concept-json-str (json/generate-string concept)
        response (client/post (str (conn/root-url conn) "/concepts")
                              (merge
                               (config/conn-params conn)
                               {:body concept-json-str
                                :content-type :json
                                :accept :json
                                :throw-exceptions false
                                :headers (ch/context->http-headers context)}))
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
