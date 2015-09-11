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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn create-tag
  "Sends a request to create the tag on the Search API. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context tag]
   (create-tag context tag {}))
  ([context tag {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn tags-url
                 :method :post
                 :raw? is-raw?
                 :http-options (merge {:body (json/generate-string tag)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn get-tag
  "Sends a request to get a tag on the Search API by concept id. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (get-tag context concept-id {}))
  ([context concept-id {:keys [is-raw? http-options]}]
   (h/request context :search
              {:url-fn #(tag-url % concept-id)
               :method :get
               :raw? is-raw?
               :http-options (merge {:accept :json} http-options)})))

(defn update-tag
  "Sends a request to update the tag on the Search API. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when updating the tag. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id tag]
   (update-tag context concept-id tag {}))
  ([context concept-id tag {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :search
                {:url-fn #(tag-url % concept-id)
                 :method :put
                 :raw? is-raw?
                 :http-options (merge {:body (json/generate-string tag)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))