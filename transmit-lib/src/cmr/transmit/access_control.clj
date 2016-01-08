(ns cmr.transmit.access-control
  "This contains functions for interacting with the access control API."
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

;; TODO add health function

(defn- health-url
  [conn]
  (format "%s/health" (conn/root-url conn)))

(defn- groups-url
  [conn]
  (format "%s/groups" (conn/root-url conn)))

(defn- group-url
  [conn group-id]
  (str (groups-url conn) "/" group-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn reset
  "Resets the access control service"
  ([context]
   (reset context false))
  ([context is-raw]
   (h/request context :access-control {:url-fn reset-url, :method :post, :raw? is-raw})))


;; TODO these are basically identical to the tag functions. Can we refactor somehow to avoid duplication?

(defn create-group
  "Sends a request to create the group on the Access Control API. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the group. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context group]
   (create-group context group nil))
  ([context group {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :access-control
                {:url-fn groups-url
                 :method :post
                 :raw? is-raw?
                 :http-options (merge {:body (json/generate-string group)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))

(defn delete-group
  "Sends a request to delete the group on the Access Control API. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when creating the token. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (delete-group context concept-id nil))
  ([context concept-id {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :access-control
                {:url-fn #(group-url % concept-id)
                 :method :delete
                 :raw? is-raw?
                 :http-options (merge {:headers headers :accept :json}
                                      http-options)}))))

(defn get-group
  "Sends a request to get a group on the Access Control API by concept id. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (get-group context concept-id nil))
  ([context concept-id {:keys [is-raw? http-options]}]
   (h/request context :access-control
              {:url-fn #(group-url % concept-id)
               :method :get
               :raw? is-raw?
               :http-options (merge {:accept :json} http-options)})))

(defn search-for-groups
  "Sends a request to find groups by the given parameters. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * http-options - Other http-options to be sent to clj-http."
  ([context params]
   (search-for-groups context params nil))
  ([context params {:keys [is-raw? http-options]}]
   (h/request context :access-control
              {:url-fn groups-url
               :method :get
               :raw? is-raw?
               :http-options (merge {:accept :json :query-params params}
                                    http-options)})))

(defn update-group
  "Sends a request to update the group on the Access Control API. Valid options are
  * :is-raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use when updating the group. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id group]
   (update-group context concept-id group nil))
  ([context concept-id group {:keys [is-raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :access-control
                {:url-fn #(group-url % concept-id)
                 :method :put
                 :raw? is-raw?
                 :http-options (merge {:body (json/generate-string group)
                                       :content-type :json
                                       :headers headers
                                       :accept :json}
                                      http-options)}))))