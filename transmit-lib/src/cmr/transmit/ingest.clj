(ns cmr.transmit.ingest
  "Provide functions to invoke ingest app"
  (:require [cmr.transmit.connection :as conn]
            [ring.util.codec :as codec]
            [cmr.transmit.http-helper :as h]
            [camel-snake-kebab.core :as csk]
            [cmr.common.util :as util :refer [defn-timed]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(def concept-type->url-part
  {:collection "collections"
   :granule "granules"})

(defn- concept-ingest-url
  [provider-id concept-type native-id conn]
  {:pre [provider-id concept-type native-id conn]}
  (format "%s/providers/%s/%s/%s"
          (conn/root-url conn)
          (codec/url-encode provider-id)
          (concept-type->url-part concept-type)
          (codec/url-encode native-id)))

(defn- health-url
  [conn]
  (format "%s/health" (conn/root-url conn)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn-timed ingest-concept
  "Send a request to ingest service to ingest a concept using the optional headers"
  ([context concept headers]
   (ingest-concept context concept headers false))
  ([context concept headers raw]
   (let [{:keys [provider-id concept-type metadata native-id revision-id]} concept]
     (h/request context :ingest
                {:url-fn #(concept-ingest-url provider-id concept-type native-id %)
                 :method :put
                 :raw? raw
                 :http-options {:body metadata
                                :content-type (:format concept)
                                :headers headers
                                :accept :json}}))))
(defn-timed delete-concept
  "Send a request to ingest service to delete a concept using the optional headers"
  ([context concept headers]
   (ingest-concept context concept headers false))
  ([context concept headers raw]
   (let [{:keys [provider-id concept-type native-id revision-id]} concept]
     (h/request context :ingest
                {:url-fn #(concept-ingest-url provider-id concept-type native-id %)
                 :method :delete
                 :raw? raw
                 :http-options {:headers headers
                                :accept :json}}))))

;; Defines health check function
(h/defhealther get-ingest-health :ingest {:timeout-secs 2})
