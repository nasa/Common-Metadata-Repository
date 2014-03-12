(ns cmr-system-int-test.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.data.xml :as xml]
            [cmr-system-int-test.url-helper :as url]))

(defn find-collection-refs
  "Returns the colletion references that are found
  by searching with the input params"
  [params]
  (let [url (str (url/search-url params))
        response (client/get url)
        body (:body response)
        result (:content (xml/parse (java.io.StringReader. body)))]
    (is (= 200 (:status response)))
    (map (fn [x]
           (let [elem (:content x)
                 dataset-id (first (:content (first elem)))
                 echo-concept-id (first (:content (second elem)))
                 location (first (:content (nth elem 2)))]
             {:dataset-id dataset-id
              :echo-concept-id echo-concept-id
              :location location}))
         result)))


(comment
  (let [result (find-collection-refs {:provider "CMR_PROV1"})
        xml (:body result)
        c (:content (xml/parse (java.io.StringReader. xml)))]
    (map (fn [x]
           (let [elem (:content x)
                 dataset-id (first (:content (first elem)))
                 echo-concept-id (first (:content (second elem)))
                 location (first (:content (nth elem 2)))]
             {:dataset-id dataset-id
              :echo-concept-id echo-concept-id
              :location location}) )
         c)
    ))
