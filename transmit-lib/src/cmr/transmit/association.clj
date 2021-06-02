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

(defmethod associations-url :tool
  [context concept-id]
  (associations-by-concept-ids-url context :tool concept-id))

(defmethod associations-url :variable
  [context concept-id]
  (associations-by-concept-ids-url context :variable concept-id))

(defmulti single-association-url
  (fn [context concept-id associated-concept-id]
    (->> [concept-id associated-concept-id]
         (map #(concepts/parse-concept-id %))
         (map :concept-type))))

(defmethod single-association-url [:variable :collection]
  [context concept-id associated-concept-id]
  (format "%s/associations/variables/%s/collections/%s"
          (conn/root-url context)
          concept-id
          associated-concept-id))

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

(defn associate-single-concept
  "Sends a request to associate the concept with a collection based on the concept type.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id assoc-concept-id]
   (associate-single-concept context concept-id assoc-concept-id nil))
  ([context concept-id assoc-concept-id {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(single-association-url % concept-id assoc-concept-id)
                 :method :post
                 :raw? raw?
                 :http-options (merge {:content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn dissociate-single-concept
  "Sends a request to dissociate the concept with a collection based on the concept type.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id assoc-concept-id]
   (dissociate-single-concept context concept-id assoc-concept-id nil))
  ([context concept-id assoc-concept-id {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(single-association-url % concept-id assoc-concept-id)
                 :method :delete
                 :raw? raw?
                 :http-options (merge {:content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
