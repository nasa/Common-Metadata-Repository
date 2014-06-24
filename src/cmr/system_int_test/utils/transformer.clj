(ns cmr.system-int-test.utils.transformer
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.concepts :as cs]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.util :as u]
            [camel-snake-kebab :as csk]
            [clojure.walk]
            [ring.util.codec :as codec]
            [cmr.umm.core :as ummc]))

(defn transform-concepts
  "Transform concepts given as concept-id, revision tuples into the given format"
  [concepts format]
  (let [response (client/post (url/transformer-url)
                              {:accept format
                               :throw-exceptions false
                               :content-type "application/json"
                               :body (json/encode concepts)})]
    response))

(defn decode-response
  "Decode the vector of xml out of the response from a transform request."
  [response]
  (json/decode (:body response) true))

(defn transform-and-compare
  "Transform a collection of concepts/revision-ids and compare them to the expected result."
  [concept-rev]
  (let [umm (map first concept-rev)
        xml (map #(ummc/umm->xml % :echo10) umm)
        tuples (map #(vector (:concept-id (first %)) (last %)) concept-rev)
        resp (transform-concepts tuples "application/echo10+xml")
        xml-resp (decode-response resp)]
    (= xml xml-resp)))