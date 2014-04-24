(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.utils.url-helper :as url]))


(defn find-refs
  "Returns the references that are found by searching with the input params"
  [concept-type params]
  (let [response (client/get (url/search-url concept-type)
                             {:accept :json
                              :query-params params})]
    (is (= 200 (:status response)))
    (-> response
        :body
        (cheshire/decode true)
        :references)))

(defn collection-concept
  "Creates a collection concept"
  [provider-id uniq-num]
  {:short-name (str "short" uniq-num)
   :version-id (str "V" uniq-num)
   :long-name (str "A minimal valid collection" uniq-num)
   :entry-title (str "MinimalCollection" uniq-num "V1")})