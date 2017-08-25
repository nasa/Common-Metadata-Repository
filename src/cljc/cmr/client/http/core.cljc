(ns cmr.client.http.core
  (:require
   #?(:clj [cmr.client.http.impl.cljhttp :as http
                                         :refer [->HTTPClientData]]
      :cljs [cmr.client.http.impl.cljshttp :as http
                                           :refer [HTTPClientData]
                                           :include-macros true]))
  #?(:clj
    (:import (cmr.client.http.impl.cljhttp HTTPClientData)))
  (:refer-clojure :exclude [get]))

(defprotocol HTTPClient
  "A common interface for both Clojure and ClojureScript HTTP clients."
  (get [this url] [this url opts])
  (head [this url] [this url opts])
  (put [this url] [this url opts])
  (post [this url] [this url opts])
  (delete [this url] [this url opts])
  (copy [this url] [this url opts])
  (move [this url] [this url opts])
  (patch [this url] [this url opts])
  (options [this url] [this url opts]))

(extend HTTPClientData
        HTTPClient
        http/client-behaviour)

(defn create-client
  ([]
   (create-client {}))
  ([http-options]
   (create-client {} http-options))
  ([parent-client-options http-options]
   (->HTTPClientData parent-client-options
                     http-options)))
