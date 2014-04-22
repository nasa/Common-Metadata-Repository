(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn find-collection-refs
  "Returns the collection references that are found
  by searching with the input params"
  [params]
  (let [url (str (url/collection-search-url params))
        response (client/get url {:accept :json})
        body (:body response)
        result (cheshire/decode body)
        references (result "references")]
    (is (= 200 (:status response)))
    (map (fn [x]
           (let [{:strs [name concept-id revision-id]} x]
             {:dataset-id name
              :concept-id concept-id
              :revision-id revision-id}))
         references)))

(defn find-granule-refs
  "Returns the granule references that are found
  by searching with the input params"
  [params]
  (let [url (str (url/granule-search-url params))
        response (client/get url {:accept :json})
        body (:body response)
        result (cheshire/decode body)
        references (result "references")]
    (is (= 200 (:status response)))
    (map (fn [x]
           (let [{:strs [name concept-id revision-id]} x]
             {:granule-ur name
              :concept-id concept-id
              :revision-id revision-id}))
         references)))


(defn collection-concept
  "Creates a collection concept"
  [provider-id uniq-num]
  {:short-name (str "short" uniq-num)
   :version-id (str "V" uniq-num)
   :long-name (str "A minimal valid collection" uniq-num)
   :entry-title (str "MinimalCollection" uniq-num "V1")})
