(ns cmr.transmit.metadata-db2
  "This contains functions for interacting with the metadata db API. It uses the newer transmit namespace
  style that concepts, and access control use"
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [cmr.common.api.context :as ch]
    [cmr.common.services.errors :as errors]
    [cmr.transmit.config :as config]
    [cmr.transmit.connection :as conn]
    [cmr.transmit.http-helper :as http-helper]
    [ring.util.codec :as codec]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- providers-url
  [conn]
  (format "%s/providers" (conn/root-url conn)))

(defn- provider-url
  [conn provider-id]
  (format "%s/%s" (providers-url conn) provider-id))

(defn- concepts-url
  [conn]
  (format "%s/concepts" (conn/root-url conn)))

(defn- concept-search-url
  [conn concept-type]
  (format "%s/concepts/search/%s" (conn/root-url conn) (name concept-type)))

(defn- latest-concept-url
  [conn concept-id]
  (str (concepts-url conn) "/" concept-id))

(defn- concept-revision-url
  [conn concept-id revision-id]
  (str (concepts-url conn) "/" concept-id "/" revision-id))

(defn- concept-id-url
  [conn concept-type provider-id native-id]
  (str (conn/root-url conn)
       "/concept-id/" (name concept-type) "/" provider-id "/" (codec/url-encode native-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper functions

(defn finish-parse-concept
  "Finishes the parsing of a concept. After a concept has been parsed from JSON some of its fields
  may still be a String instead of a native clojure types."
  [concept]
  (when concept
    (update-in concept [:concept-type] keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions
(declare reset)
(http-helper/defresetter reset :metadata-db)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Provider functions
(declare create-provider update-provider delete-provider)
(http-helper/defcreator create-provider :metadata-db providers-url {:use-system-token? true})
(http-helper/defupdater update-provider :metadata-db provider-url {:use-system-token? true})
(http-helper/defdestroyer delete-provider :metadata-db provider-url {:use-system-token? true})

(defn get-providers
  "Returns the list of providers configured in the metadata db. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context]
   (get-providers context nil))
  ([context {:keys [raw? http-options token]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (http-helper/request context :metadata-db
                          {:url-fn providers-url
                 :method :get
                 :raw? raw?
                 :http-options (http-helper/include-request-id context (merge {:accept :json
                                                                               :headers headers}
                                                                              http-options))}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Concept functions
(declare save-concept)
(http-helper/defcreator save-concept :metadata-db concepts-url {:use-system-token? true})

(defn get-concept-id
  "Returns a concept id for the given concept type, provider, and native id"
  ([context concept-type provider-id native-id]
   (get-concept-id context concept-type provider-id native-id nil))
  ([context concept-type provider-id native-id {:keys [raw? http-options]}]
   (let [response (http-helper/request context :metadata-db
                                       {:url-fn #(concept-id-url % concept-type provider-id native-id)
                                        :method :get
                                        :raw? raw?
                                        :use-system-token? true
                                        :http-options (http-helper/include-request-id context (merge {:accept :json} http-options))})]
     (if raw?
       response
       (:concept-id response)))))

(defn find-concepts
 "Searches metadata db for concepts matching the given parameters.
 Valid options are:
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
 ([context params concept-type]
  (find-concepts context params concept-type nil))
 ([context params concept-type {:keys [raw? http-options]}]
  (-> context
      (http-helper/request :metadata-db
                           {:url-fn #(concept-search-url % concept-type)
                            :method :get
                            :raw? raw?
                            :use-system-token? true
                            :http-options (http-helper/include-request-id context (merge {:accept :json} http-options params))})
      finish-parse-concept)))

(defn get-concept
  "Retrieve the concept with the given concept and revision-id. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id revision-id]
   (get-concept context concept-id revision-id nil))
  ([context concept-id revision-id {:keys [raw? http-options]}]
   (-> context
       (http-helper/request :metadata-db
                            {:url-fn #(concept-revision-url % concept-id revision-id)
                   :method :get
                   :raw? raw?
                   :use-system-token? true
                   :http-options (http-helper/include-request-id context (merge {:accept :json} http-options))})
       finish-parse-concept)))

(defn get-latest-concept
  "Retrieve the latest verison of a concept. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (get-latest-concept context concept-id nil))
  ([context concept-id {:keys [raw? http-options]}]
   (-> context
       (http-helper/request :metadata-db
                            {:url-fn #(latest-concept-url % concept-id)
                   :method :get
                   :raw? raw?
                   :use-system-token? true
                   :http-options (http-helper/include-request-id context (merge {:accept :json} http-options))})
       finish-parse-concept)))

;; Defines health check function
(declare get-metadata-db-health)
(http-helper/defhealther get-metadata-db-health :metadata-db {:timeout-secs 2})


(defn get-subscription-cache-content
  "Retrieves the cache contents of the ingest subscription cache."
  ([context coll-concept-id]
   (get-subscription-cache-content context coll-concept-id nil))
  ([context coll-concept-id {:keys [raw? http-options]}]
   (let [conn (config/context->app-connection context :metadata-db)
        request-url (str (conn/root-url conn) "/subscription/cache-content")
        params (merge
                 (config/conn-params conn)
                 {:accept :json
                  :query-params {:collection-concept-id coll-concept-id}
                  :headers (merge
                             (ch/context->http-headers context)
                             {:client-id config/cmr-client-id})
                  :throw-exceptions false
                  :http-options (http-helper/include-request-id context {})})
        response (client/get request-url params)
         {:keys [status body]} response
         status (int status)]
     (case status
       200 (json/decode body true)
       ;; default
       (errors/internal-error!
          (format "Get subscription cache content failed for collection %s. status: %s body: %s" coll-concept-id status body))))))
