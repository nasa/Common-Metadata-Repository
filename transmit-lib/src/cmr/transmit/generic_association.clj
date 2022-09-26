(ns cmr.transmit.generic-association
  "This contains functions for interacting with the generic association API."
  (:require
   [cheshire.core :as json]
   [cmr.common.concepts :as concepts]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

(defn- generic-association-url-for-concept-revision
  "Returns the generic association url for the given concept-id and revision-id."
  [conn concept-id revision-id]
  (if revision-id
    (format "%s/associate/%s/%s"
            (conn/root-url conn)
            concept-id
            revision-id)
    (format "%s/associate/%s"
            (conn/root-url conn)
            concept-id)))

(defn associate-concept
  "Sends a request to associate the concept with other concepts.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id revision-id content]
   (associate-concept context concept-id revision-id content nil))
  ([context concept-id revision-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(generic-association-url-for-concept-revision % concept-id revision-id)
                 :method :post
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn dissociate-concept
  "Sends a request to dissociate the concept with concepts.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id revision-id content]
   (dissociate-concept context concept-id revision-id content nil))
  ([context concept-id revision-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(generic-association-url-for-concept-revision % concept-id revision-id)
                 :method :delete
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
