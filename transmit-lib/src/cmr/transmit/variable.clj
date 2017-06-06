(ns cmr.transmit.variable
  "This contains functions for interacting with the variable API."
  (:require
   [cheshire.core :as json]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]
   [ring.util.codec :as codec]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- variables-url
  [conn]
  (format "%s/variables" (conn/root-url conn)))

(defn- variable-url
  [conn variable-name]
  (str (variables-url conn) "/" variable-name))

(defn- variable-associations-by-concept-ids-url
  [conn variable-name]
  (str (variable-url conn variable-name) "/associations"))

;; This function is for the future when we add the associate collections to variables by query
(defn- variable-associations-by-query-url
  [conn variable-name]
  (str (variable-associations-by-concept-ids-url conn variable-name) "/by_query"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(h/defcreator create-variable :ingest variables-url)
(h/defupdater update-variable :ingest variable-url)
(h/defdestroyer delete-variable :ingest variable-url)

(defmulti variable-associations-url
  "Returns the url to associate a variable based on the association type.
  Valid association types are :query and :concept-ids."
  (fn [context variable-name association-type]
    association-type))

(defmethod variable-associations-url :query
  [context variable-name _]
  (variable-associations-by-query-url context variable-name))

(defmethod variable-associations-url :concept-ids
  [context variable-name _]
  (variable-associations-by-concept-ids-url context variable-name))

(defn associate-variable
  "Sends a request to associate the variable with collections based on the given association type.
  Valid association type are :query and :concept-ids.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([association-type context variable-key content]
   (associate-variable association-type context variable-key content nil))
  ([association-type context variable-key content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(variable-associations-url % variable-key association-type)
                 :method :post
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn dissociate-variable
  "Sends a request to dissociate the variable with collections based on the given association type.
  Valid association type are :query and :concept-ids.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([association-type context concept-id content]
   (dissociate-variable context concept-id content nil))
  ([association-type context concept-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(variable-associations-url % concept-id association-type)
                 :method :delete
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
