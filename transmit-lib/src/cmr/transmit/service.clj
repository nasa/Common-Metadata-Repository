(ns cmr.transmit.service
  "This contains functions for interacting with the service API."
  (:require
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- services-url
  [conn]
  (format "%s/services" (conn/root-url conn)))

(defn- service-url
  [conn service-id]
  (str (services-url conn) "/" service-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(h/defcreator create-service :ingest services-url {:accept :xml})
(h/defupdater update-service :ingest service-url {:accept :xml})
