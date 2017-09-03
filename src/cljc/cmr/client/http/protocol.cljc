(ns cmr.client.http.protocol
  (:refer-clojure :exclude [get]))

(defprotocol HTTPClientAPI
  "An interface for ClojureScript HTTP clients."
  (^:export get [this url] [this url opts]
    "Perform an HTTP `GET`")
  (^:export head [this url] [this url opts]
    "Perform an HTTP `HEAD`")
  (^:export put [this url] [this url opts]
    "Perform an HTTP `PUT`")
  (^:export post [this url] [this url opts]
    "Perform an HTTP `POST`")
  (^:export delete [this url] [this url opts]
    "Perform an HTTP `DELETE`")
  (^:export copy [this url] [this url opts]
    "Perform an HTTP `COPY`")
  (^:export move [this url] [this url opts]
    "Perform an HTTP `MOVE`")
  (^:export patch [this url] [this url opts]
    "Perform an HTTP `PATCH`")
  (^:export options [this url] [this url opts]
    "Perform an HTTP `OPTIONS`"))
