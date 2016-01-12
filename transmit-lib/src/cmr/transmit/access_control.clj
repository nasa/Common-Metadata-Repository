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

(h/defcreator create-group :access-control groups-url)
(h/defsearcher search-for-groups :access-control groups-url)
(h/defupdater update-group :access-control group-url)
(h/defdestroyer delete-group :access-control group-url)
(h/defgetter get-group :access-control group-url)


#_(defn search-for-groups
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

