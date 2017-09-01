(ns cmr.client.http.protocol
  (:refer-clojure :exclude [get]))

(defprotocol HTTPClientAPI
  "An interface for ClojureScript HTTP clients."
  (^:export get [this url] [this url opts])
  (^:export head [this url] [this url opts])
  (^:export put [this url] [this url opts])
  (^:export post [this url] [this url opts])
  (^:export delete [this url] [this url opts])
  (^:export copy [this url] [this url opts])
  (^:export move [this url] [this url opts])
  (^:export patch [this url] [this url opts])
  (^:export options [this url] [this url opts]))
