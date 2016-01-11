(ns cmr.transmit.metadata-db2
  "This contains functions for interacting with the metadata db API. It uses the newer transmit namespace
  style that cubby, concepts, and access control use"
  (:require [cmr.transmit.connection :as conn]
            [cmr.transmit.config :as config]
            [ring.util.codec :as codec]
            [cmr.transmit.http-helper :as h]
            [cheshire.core :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- reset-url
  [conn]
  (format "%s/reset" (conn/root-url conn)))

(defn- concepts-url
  [conn]
  (format "%s/concepts" (conn/root-url conn)))

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

(defn reset
  "Resets the access control service"
  ([context]
   (reset context false))
  ([context is-raw]
   (h/request context :metadata-db {:url-fn reset-url, :method :post, :raw? is-raw})))

(defn save-concept
  "Sends a request to save the concept. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept]
   (save-concept context concept nil))
  ([context concept {:keys [is-raw? http-options]}]
   (h/request context :metadata-db
              {:url-fn concepts-url
               :method :post
               :raw? is-raw?
               :http-options (merge {:body (json/generate-string concept)
                                     :content-type :json
                                     :accept :json}
                                    http-options)})))

(defn get-concept-id
  "Returns a concept id for the given concept type, provider, and native id"
  ([context concept-type provider-id native-id]
   (get-concept-id context concept-type provider-id native-id nil))
  ([context concept-type provider-id native-id {:keys [is-raw? http-options]}]
   (let [response (h/request context :metadata-db
                             {:url-fn #(concept-id-url % concept-type provider-id native-id)
                              :method :get
                              :raw? is-raw?
                              :http-options (merge {:accept :json} http-options)})]
     (if is-raw?
       response
       (:concept-id response)))))

(defn get-concept
  "Retrieve the concept with the given concept and revision-id. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id revision-id]
   (get-concept context concept-id revision-id nil))
  ([context concept-id revision-id {:keys [is-raw? http-options]}]
   (-> (h/request context :metadata-db
                  {:url-fn #(concept-revision-url % concept-id revision-id)
                   :method :get
                   :raw? is-raw?
                   :http-options (merge {:accept :json} http-options)})
       finish-parse-concept)))

(defn get-latest-concept
  "Retrieve the latest verison of a concept. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (get-latest-concept context concept-id nil))
  ([context concept-id {:keys [is-raw? http-options]}]
   (-> (h/request context :metadata-db
                  {:url-fn #(latest-concept-url % concept-id)
                   :method :get
                   :raw? is-raw?
                   :http-options (merge {:accept :json} http-options)})
       finish-parse-concept)))

