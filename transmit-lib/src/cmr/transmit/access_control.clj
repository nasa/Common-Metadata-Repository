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

;; TODO CMR-2351 - add health function

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
  ([context raw]
   (h/request context :access-control {:url-fn reset-url, :method :post, :raw? raw})))

; Group CRUD functions
(h/defcreator create-group :access-control groups-url)
(h/defsearcher search-for-groups :access-control groups-url)
(h/defupdater update-group :access-control group-url)
(h/defdestroyer delete-group :access-control group-url)
(h/defgetter get-group :access-control group-url)
