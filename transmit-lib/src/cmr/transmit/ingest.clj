(ns cmr.transmit.ingest
  "Provide functions to invoke ingest app"
  (:require [cmr.common.services.health-helper :as hh]
            [cmr.transmit.connection :as conn]
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
  (format "%s/providers/%s/%s/%s"
          (conn/root-url conn)
          (codec/url-encode provider-id)
          (concept-type->url-part concept-type)
          (codec/url-encode native-id)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(defn-timed ingest-concept
  ([context concept]
   (ingest-concept context concept false))
  ([context concept is-raw]
   (let [{:keys [provider-id concept-type metadata native-id revision-id]} concept]
     (h/request context :ingest
                {:url-fn #(concept-ingest-url provider-id concept-type native-id %)
                 :method :put
                 :raw? is-raw
                 :http-options {:body metadata
                                :content-type (:format concept)
                                :headers {"Revision-Id" revision-id}
                                :accept :json}}))))

(defn-timed delete-concept
  ([context concept]
   (delete-concept context concept false))
  ([context concept is-raw]
   (let [{:keys [provider-id concept-type native-id revision-id]} concept]
     (h/request context :ingest
                {:url-fn #(concept-ingest-url provider-id concept-type native-id %)
                 :method :delete
                 :raw? is-raw
                 :http-options {:headers {"Revision-Id" revision-id}
                                :accept :json}}))))