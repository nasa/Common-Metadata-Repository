(ns cmr.transmit.tag
  "This contains functions for interacting with the tagging API."
  (:require [cmr.transmit.connection :as conn]
            [cmr.transmit.config :as config]
            [ring.util.codec :as codec]
            [cmr.transmit.http-helper :as h]
            [cheshire.core :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- tags-url
  [conn]
  (format "%s/tags" (conn/root-url conn)))

(defn- tag-url
  [conn tag-id]
  (str (tags-url conn) "/" tag-id))

(defn- tag-associations-by-concept-ids-url
  [conn tag-id]
  (str (tag-url conn tag-id) "/associations"))

(defn- tag-associations-by-query-url
  [conn tag-id]
  (str (tag-associations-by-concept-ids-url conn tag-id) "/by_query"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(h/defcreator create-tag :search tags-url)
(h/defsearcher search-for-tags :search tags-url)
(h/defupdater update-tag :search tag-url)
(h/defdestroyer delete-tag :search tag-url)
(h/defgetter get-tag :search tag-url)

(defmulti tag-associations-url
  "Returns the url to associate a tag based on the association type.
  Valid association types are :query and :concept-ids."
  (fn [context concept-id association-type]
    association-type))

(defmethod tag-associations-url :query
  [context concept-id _]
  (tag-associations-by-query-url context concept-id))

(defmethod tag-associations-url :concept-ids
  [context concept-id _]
  (tag-associations-by-concept-ids-url context concept-id))

(defn associate-tag
  "Sends a request to associate the tag with collections based on the given association type.
  Valid association type are :query and :concept-ids.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([association-type context concept-id content]
   (associate-tag association-type context concept-id content nil))
  ([association-type context concept-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(tag-associations-url % concept-id association-type)
                 :method :post
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn disassociate-tag
  "Sends a request to disassociate the tag with collections based on the given association type.
  Valid association type are :query and :concept-ids.
  Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([association-type context concept-id content]
   (disassociate-tag context concept-id content nil))
  ([association-type context concept-id content {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(tag-associations-url % concept-id association-type)
                 :method :delete
                 :raw? raw?
                 :http-options (merge {:body (json/generate-string content)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
