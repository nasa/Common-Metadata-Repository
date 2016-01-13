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

(defn- tag-associations-by-query-url
  [conn tag-id]
  (str (tag-url conn tag-id) "/associations/by_query"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(h/defcreator create-tag :search tags-url)
(h/defsearcher search-for-tags :search tags-url)
(h/defupdater update-tag :search tag-url)
(h/defdestroyer delete-tag :search tag-url)
(h/defgetter get-tag :search tag-url)

(defn associate-tag-by-query
  "Sends a request to associate the tag with collections found with a JSON query. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id query]
   (associate-tag-by-query context concept-id query nil))
  ([context concept-id query {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(tag-associations-by-query-url % concept-id)
                 :method :post
                 :raw? is-raw?
                 :http-options (merge {:body (json/generate-string query)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn disassociate-tag-by-query
  "Sends a request to disassociate the tag with collections found with a JSON query. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id query]
   (disassociate-tag-by-query context concept-id query nil))
  ([context concept-id query {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(tag-associations-by-query-url % concept-id)
                 :method :delete
                 :raw? is-raw?
                 :http-options (merge {:body (json/generate-string query)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))
