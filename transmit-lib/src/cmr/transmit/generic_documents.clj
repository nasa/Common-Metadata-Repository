(ns cmr.transmit.generic-documents
  "This namespace handles retrieval of generic documents"
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.data.csv :as csv]
   [clojure.data.xml :as xml]
   [clojure.java.io :as jio]
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.util :as util]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.transmit.config :as config]
   [cmr.transmit.connection :as conn]
   [cmr.transmit.http-helper :as http-helper]
   [ring.util.codec :as codec]))

(defn- concept-ingest-url
  "Generate a URL to the metadata db for posting to the generic APIs"
  ([conn provider-id]
   {:pre [conn provider-id]}
   (format "%s/generics/%s" (conn/root-url conn) (codec/url-encode provider-id)))
  ([conn provider-id concept-id]
   {:pre [conn concept-id]}
   (format "%s/generics/%s/%s"
           (conn/root-url conn)
           (codec/url-encode provider-id)
           (codec/url-encode concept-id))))

(defn create-generic
  "Sends a request to create the item. Valid options are
  * token - the user token to use when creating the item. If not set the token in the context will
    be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context provider-id item]
   (create-generic context provider-id item nil))
  ([context provider-id item options]
   (let [{token :token http-options :http-options} options
         token (or token (:token context))
         headers (when token {config/token-header token})]
     (http-helper/request context :metadata-db
                          {:url-fn #(concept-ingest-url % provider-id)
                           :method :post
                           :raw? true
                           :http-options (merge {:body (json/generate-string item)
                                                 :content-type :json
                                                 :headers headers
                                                 :accept :json}
                                                http-options)}))))

(defn update-generic
  "Sends a request to update the item. Valid options are
  * token - the user token to use when creating the group. If not set the token in the context will be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context provider-id concept-id item]
   (update-generic context provider-id concept-id item nil))
  ([context provider-id concept-id item options]
   (println "here in manual update-generic function...")
   (let [{token :token http-options :http-options revision-id :revision-id} options
         token (or token (:token context))
         headers (when token {config/token-header token})
         headers (if revision-id
                   (assoc headers config/revision-id-header revision-id)
                   headers)]
     (http-helper/request context :metadata-db
                          {:url-fn #(concept-ingest-url % provider-id concept-id)
                           :method :put
                           :raw? true
                           :http-options (merge {:body (json/generate-string item)
                                                 :content-type :json
                                                 :headers headers
                                                 :accept :json}
                                                http-options)}))))

;(http-helper/defcreator create-generic :metadata-db concept-ingest-url {:use-system-token? true})
(http-helper/defgetter read-generic :metadata-db concept-ingest-url {:use-system-token? true :raw? true})
;(http-helper/defupdater update-provider :metadata-db concept-ingest-url {:use-system-token? true})
(http-helper/defdestroyer delete-provider :metadata-db concept-ingest-url {:use-system-token? true})




(comment 

(let [context {:system (cmr.indexer.system/create-system)}
      conn (config/context->app-connection context :metadata-db)]
  (println (concept-ingest-url conn "PROV1"))
  (println (concept-ingest-url conn "PROV1" "X12000000001-PROV1"))

  ;(read-generic context "X12000001-PROV")

  ;(clojure.repl/source cmr.transmit.generic-documents/read-generic)
  )

)