(ns cmr.client.http.impl.cljhttp
  (:require
   [clj-http.client :as http]
   [clojure.core.async :as async]
   [cmr.client.http.impl.util :as util])
  (:refer-clojure :exclude [get]))

(defrecord HTTPClientData [
  parent-client-options
  options])

(defn- client-callback
  "When we get the response, write it to the channel."
  [this chan response]
  (async/>!! chan (if (get-in this [:parent-client-options :return-body?])
                    (:body (util/parse-body! response))
                    (util/parse-body! response))))

(defn- get
  ([this url]
    (get this url (util/get-default-options this)))
  ([this url options]
    (let [chan (async/chan)]
      (http/get url
                (merge (:options this) options)
                (partial client-callback this chan)
                (partial client-callback this chan))
      chan)))

(defn- head
  ([this url]
    (head this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- put
  ([this url]
    (put this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- post
  ([this url]
    (post this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- delete
  ([this url]
    (delete this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- copy
  ([this url]
    (copy this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- move
  ([this url]
    (move this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- patch
  ([this url]
    (patch this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(defn- options
  ([this url]
    (options this url (util/get-default-options this)))
  ([this url options]
    :not-implemented))

(def client-behaviour
  {:get get
   :head head
   :put put
   :post post
   :delete delete
   :copy copy
   :move move
   :patch patch
   :options options})
