(ns ^{:doc "provides search related utilities."}
  cmr-system-int-test.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [cmr-system-int-test.url-helper :as url]
            [cmr.system-trace.http :as h]))

(defn find-collection-refs
  "Returns the collection references that are found
  by searching with the input params"
  [context params]
  (let [url (str (url/collection-search-url params))
        response (client/get url {:accept :json
                                  :headers (h/context->http-headers context)})
        body (:body response)
        result (cheshire/decode body)
        references (result "references")]
    (is (= 200 (:status response)))
    (map (fn [x]
           (let [{:strs [native-id concept-id revision-id]} x]
             {:dataset-id native-id
              :concept-id concept-id
              :revision-id revision-id}))
         references)))
