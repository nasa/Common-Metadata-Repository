(ns cmr.client.http.protocol
  "This namespace defines the protocols used by the CMR HTTP client."
  (:refer-clojure :exclude [get]))

(defprotocol HTTPClientAPI
  "An interface for ClojureScript HTTP clients."
  (^:export get [this url] [this url opts]
    "Perform an HTTP `GET`.")
  (^:export head [this url] [this url opts]
    "Perform an HTTP `HEAD`.")
  (^:export put [this url] [this url data] [this url data opts]
    "Perform an HTTP `PUT`.")
  (^:export post [this url data] [this url data opts]
    "Perform an HTTP `POST`.")
  (^:export delete [this url] [this url opts]
    "Perform an HTTP `DELETE`.

    Not yet implemented.")
  (^:export copy [this url] [this url opts]
    "Perform an HTTP `COPY`.

    Not yet implemented.")
  (^:export move [this url] [this url opts]
    "Perform an HTTP `MOVE`.

    Not yet implemented.")
  (^:export patch [this url] [this url opts]
    "Perform an HTTP `PATCH`.

    Not yet implemented.")
  (^:export options [this url] [this url opts]
    "Perform an HTTP `OPTIONS`.

    Not yet implemented."))
