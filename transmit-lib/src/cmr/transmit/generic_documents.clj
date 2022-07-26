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
   (println "in concept ingest url")
   (format "%s/generics/%s" (conn/root-url conn) (codec/url-encode provider-id)))
  ([conn provider-id concept-id]
   {:pre [conn concept-id]}
   (format "%s/generics/%s/%s"
           (conn/root-url conn)
           (codec/url-encode provider-id)
           (codec/url-encode concept-id))))

(defn- concept-ingest-url2
  "Generate a URL to the metadata db for posting to the generic APIs, but allow
   for a list of parameters to be passed in which will then be applied again to
   this function. This is to allow for the original calling function to specify
   both provider-id and concept-id while using the CRUD macros."
  ([conn param-list]
   {:pre [conn param-list]}
   (apply concept-ingest-url2 (into [conn] param-list)))
  
  ([conn provider-id concept-id]
   {:pre [conn provider-id concept-id]}
   (concept-ingest-url conn provider-id concept-id)))

;; These are "standard" macros for creating CRUD functions. get, update, and
;; destroy all require 2 parameters and must use a url resolving function that
;; does an apply with an array of paramaters to resolve them. The calling
;; function must pass in the URL parameters as an array. Create can use the
;; single param function.
(http-helper/defcreator2 create-generic :metadata-db concept-ingest-url {:use-system-token? true :raw? true})
(http-helper/defgetter read-generic :metadata-db concept-ingest-url2 {:use-system-token? true :raw? true})
(http-helper/defupdater update-generic :metadata-db concept-ingest-url2 {:use-system-token? true})
(http-helper/defdestroyer delete-generic :metadata-db concept-ingest-url2 {:use-system-token? true})