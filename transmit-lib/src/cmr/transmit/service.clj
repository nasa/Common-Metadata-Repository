(ns cmr.transmit.service
  "This contains functions for interacting with the service API."
  (:require
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as h]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- service-associations-url
  [conn]
  (format "%s/concepts/search/service-associations" (conn/root-url conn)))

(h/defsearcher search-for-service-associations :metadata-db service-associations-url)
