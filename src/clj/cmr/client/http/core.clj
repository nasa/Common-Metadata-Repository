(ns cmr.client.http.core
  (:require
   [cmr.client.http.impl :as impl])
  (:import (cmr.client.http.impl HTTPClientData))
  (:refer-clojure :exclude [get]))

(defprotocol HTTPClientAPI
  "An interface for Clojure HTTP clients."
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
        HTTPClientAPI
        impl/client-behaviour)

(defn create-client
  ([]
   (create-client {}))
  ([http-options]
   (create-client {} http-options))
  ([parent-client-options http-options]
   (impl/->HTTPClientData parent-client-options
                          http-options)))
