(ns cmr.transmit.metadata-db2
  "This contains functions for interacting with the metadata db API. It uses the newer transmit namespace
  style that concepts, and access control use"
  (:require
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]
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
(h/defresetter reset :metadata-db)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Provider functions

(h/defcreator create-provider :metadata-db providers-url {:use-system-token? true})
(h/defupdater update-provider :metadata-db provider-url {:use-system-token? true})
(h/defdestroyer delete-provider :metadata-db provider-url {:use-system-token? true})

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
     (h/request context :metadata-db
                {:url-fn providers-url
                 :method :get
                 :raw? raw?
                 :http-options (merge {:accept :json
                                       :headers headers}
                                      http-options)}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Concept functions

(h/defcreator save-concept :metadata-db concepts-url {:use-system-token? true})

(defn get-concept-id
  "Returns a concept id for the given concept type, provider, and native id"
  ([context concept-type provider-id native-id]
   (get-concept-id context concept-type provider-id native-id nil))
  ([context concept-type provider-id native-id {:keys [raw? http-options]}]
   (let [response (h/request context :metadata-db
                             {:url-fn #(concept-id-url % concept-type provider-id native-id)
                              :method :get
                              :raw? raw?
                              :use-system-token? true
                              :http-options (merge {:accept :json} http-options)})]
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
      (h/request :metadata-db
                 {:url-fn #(concept-search-url % concept-type)
                  :method :get
                  :raw? raw?
                  :use-system-token? true
                  :http-options (merge {:accept :json} http-options params)})
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
       (h/request :metadata-db
                  {:url-fn #(concept-revision-url % concept-id revision-id)
                   :method :get
                   :raw? raw?
                   :use-system-token? true
                   :http-options (merge {:accept :json} http-options)})
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
       (h/request :metadata-db
                  {:url-fn #(latest-concept-url % concept-id)
                   :method :get
                   :raw? raw?
                   :use-system-token? true
                   :http-options (merge {:accept :json} http-options)})
       finish-parse-concept)))

;; Defines health check function
(h/defhealther get-metadata-db-health :metadata-db {:timeout-secs 2})
