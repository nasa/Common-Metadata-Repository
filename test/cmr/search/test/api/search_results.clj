(ns cmr.search.test.api.search-results
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.data.xml :as x]
            [clojure.set :as set]
            [cmr.common.xml :as cx]
            [cmr.search.api.search-results :as s]
            [cmr.search.models.results :as r]
            [cmr.search.test.models.results :as results-gen]))

(deftest validate-search-result-mime-type-test
  (testing "valid mime types"
    (s/validate-search-result-mime-type "application/json")
    (s/validate-search-result-mime-type "application/xml")
    (s/validate-search-result-mime-type "*/*"))
  (testing "invalid mime types"
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"The mime type \[application/foo\] is not supported for search results"
          (s/validate-search-result-mime-type "application/foo")))))

(defmulti parse-search-results-response
  (fn [response-str format]
    format))

(defmethod parse-search-results-response :json
  [response-str format]
  (walk/keywordize-keys (json/parse-string response-str)))

(defn ref-xml-struct->reference
  "Converts a parsed XML reference into a map"
  [xml-struct]
  {:name (cx/string-at-path xml-struct [:name])
   :id (cx/string-at-path xml-struct [:id])
   :location (cx/string-at-path xml-struct [:location])
   :revision-id (cx/long-at-path xml-struct [:revision-id])})

(defmethod parse-search-results-response :xml
  [response-str format]
  (let [xml-struct (x/parse-str response-str)
        hits (cx/long-at-path xml-struct [:hits])
        took (cx/long-at-path xml-struct [:took])
        ref-structs (cx/content-at-path xml-struct [:references])
        references (map ref-xml-struct->reference ref-structs)]
    {:hits hits
     :took took
     :references references}))

(defn result-records->map
  "Converts the result records into a map for easy comparison."
  [search-result]
  (let [{:keys [hits took references]} search-result
        response-refs (map #(set/rename-keys % {:concept-id :id}) references)]
    {:hits hits
     :took took
     :references response-refs}))

(defspec search-result->response-test 100
  (for-all [result results-gen/results
            format (gen/elements [:json :xml])
            pretty gen/boolean]
    (let [resp (s/search-results->response nil result format pretty)
          result (result-records->map result)]
      (= result (parse-search-results-response resp format)))))
