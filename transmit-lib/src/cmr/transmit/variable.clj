(ns cmr.transmit.variable
  "This contains functions for interacting with the variable API."
  (:require
   [cheshire.core :as json]
   [cmr.common.concepts :as concepts]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]
   [ring.util.codec :as codec]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; URL functions

(defn- variables-url
  [conn]
  (format "%s/variables" (conn/root-url conn)))

(defn- variable-url
  [conn variable-name]
  (str (variables-url conn) "/" variable-name))

(defn- associations-by-concept-ids-url
  [conn concept-type concept-id]
  (format "%s/%ss/%s/associations"
          (conn/root-url conn)
          (name concept-type)
          concept-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Ingest Request functions
;;;
;;; Note: ingest functions return XML responses

(h/defcreator create-variable :ingest variables-url {:accept :xml})
(h/defupdater update-variable :ingest variable-url {:accept :xml})
(h/defdestroyer delete-variable :ingest variable-url {:accept :xml})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Search Request functions
;;;
;;; Note: search functions return JSON responses

(h/defsearcher search-for-variables :search variables-url)

(defmulti variable-associations-url
  "Returns the url to associate a variable based on the association type.
  Valid association types are :query and :concept-ids."
  (fn [context concept-id]
    (:concept-type (concepts/parse-concept-id concept-id))))

(defmethod variable-associations-url :service
  [context concept-id]
  (associations-by-concept-ids-url context :service concept-id))

(defmethod variable-associations-url :variable
  [context concept-id]
  (associations-by-concept-ids-url context :variable concept-id))

(defn associate-variable
  "Sends a request to associate the variable with collections based on the given association type.
  Valid association type are :query and :concept-ids.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context variable-key content]
   (associate-variable context variable-key content nil))
  ([context variable-key content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(variable-associations-url % variable-key)
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
  ([context concept-id content]
   (dissociate-variable context concept-id content nil))
  ([context concept-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(variable-associations-url % concept-id)
                 :method :delete
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
