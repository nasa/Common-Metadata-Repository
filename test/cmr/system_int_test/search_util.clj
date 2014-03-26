(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.url-helper :as url]))

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
           (let [{:strs [entry-title concept-id revision-id]} x]
             {:dataset-id entry-title
              :concept-id concept-id
              :revision-id revision-id}))
         references)))
