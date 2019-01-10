(ns cmr.client.http.impl
  "The Clojure implementation of the CMR service HTTP client."
  (:require
   [cljs-http.client :as http]
   [cljs.core.async :as async]
   [cmr.client.common.util :as util])
  (:refer-clojure :exclude [get])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-options
  "Default HTTP client options."
  {:with-credentials? false
   :headers {"Accept" "application/json"}})

(def result-xducer
  "A transducer for use with the HTTP client that is used to transform inputs
  from Clojure data structures to JavaScript ones."
  (map clj->js))

(def result-body-xducer
  "A transducer for use with the HTTP client that is used to extract the body
  from the inputs and transform it from a Clojure data structure to JavaScript
  one."
  (map (comp clj->js :body)))

(defn create-channel
  "Create a promise channel used by the third-party HTTP client library to
  receive responses."
  [client]
  (if (get-in client [:parent-client-options :return-body?])
    (async/promise-chan result-body-xducer)
    (async/promise-chan result-xducer)))

(defn create-http-options
  "This function is intended to be used with every call, giving the call the
  opportunity to override the HTTP client options saved when the client was
  instantiated."
  [client call-options]
  (merge default-options
         {:channel (create-channel client)}
         (:http-options client)
         call-options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord HTTPClientData [
  parent-client-options
  http-options])

(defn get
  "Perform an HTTP `GET`."
  [this url options]
  (http/get url (create-http-options this options)))

(defn head
  "Perform an HTTP `HEAD`."
  [this url options]
  (http/head url (create-http-options this options)))

(defn put
  [this url data options]
  (http/put url (merge (create-http-options this options)
                       {:form-params data})))

(defn post
  [this url data options]
  (http/post url (merge (create-http-options this options)
                        {:form-params data})))
