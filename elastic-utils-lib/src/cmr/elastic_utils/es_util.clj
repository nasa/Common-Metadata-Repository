(ns cmr.elastic-utils.es-util
  "Defines shared funcs to create es helper functionality."
  (:require
    [cheshire.core :as json]
    [clojure.string :as string]))

(defn parse-safely
  "Parses the json body from the response safely"
  [body]
  (when body
    (if (string? body)
      (json/decode body true)
      body)))

(defn decode-response
  "Decodes the response body from the given response"
  [response]
  (parse-safely (:body response)))

(defn join-names
  "Joins names together with a comma"
  [names]
  (if (sequential? names)
    (string/join "," names)
    names))

(defn url-with-path
  "Returns the url with the given path"
  [conn & path-parts]
  (let [path (->> path-parts
                  (map join-names)
                  (filter identity)
                  (string/join "/"))]
    (str (:uri conn) "/" path)))
