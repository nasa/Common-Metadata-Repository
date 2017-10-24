(ns cmr.transmit.association
  "This contains functions for interacting with the association API."
  (:require
   [cheshire.core :as json]
   [cmr.common.concepts :as concepts]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

(defn- associations-by-concept-ids-url
  "Returns the variable/service associations url for the given concept type and id."
  [conn concept-type concept-id]
  (format "%s/%ss/%s/associations"
          (conn/root-url conn)
          (name concept-type)
          concept-id))

(defmulti associations-url
  "Returns the url to associate a concept with the given concept id to collections."
  (fn [context concept-id]
    (:concept-type (concepts/parse-concept-id concept-id))))

(defmethod associations-url :service
  [context concept-id]
  (associations-by-concept-ids-url context :service concept-id))

(defmethod associations-url :variable
  [context concept-id]
  (associations-by-concept-ids-url context :variable concept-id))

(defn associate-concept
  "Sends a request to associate the concept with collections based on the concept type.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-key content]
   (associate-concept context concept-key content nil))
  ([context concept-key content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(associations-url % concept-key)
                 :method :post
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn dissociate-concept
  "Sends a request to dissociate the concept with collections based on the concept type.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id content]
   (dissociate-concept context concept-id content nil))
  ([context concept-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(associations-url % concept-id)
                 :method :delete
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
