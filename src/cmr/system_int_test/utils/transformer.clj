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
            [ring.util.codec :as codec]))

(defn transform-concepts
  "Transform concepts given as concept-id, revision tuples into the given format"
  [concepts format]
  (let [response (client/post (url/transformer-url)
                               {:accept format
                                :content-type "application/json"
                                :body (json/encode concepts)})]
    response))