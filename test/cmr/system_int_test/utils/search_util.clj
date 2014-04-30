(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.concepts :as cs]
            [cmr.system-int-test.utils.url-helper :as url]))


(defn find-refs
  "Returns the references that are found by searching with the input params"
  [concept-type params]
  (let [response (client/get (url/search-url concept-type)
                             {:accept :json
                              :query-params params})
        _ (is (= 200 (:status response)))
        result (json/decode (:body response) true)]
    (-> result
        ;; Rename references to refs
        (assoc :refs (:references result))
        (dissoc :references))))

(defn get-concept-by-concept-id
  "Returns the concept metadata by searching metadata-db using the given cmr concept id"
  [concept-id]
  (let [concept-type (cs/concept-prefix->concept-type (subs concept-id 0 1))]
    (client/get (url/retrieve-concept-url concept-type concept-id)
                {:throw-exceptions false})))

(defmacro get-search-failure-data
  "Executes a search and returns error data that was caught.
  Tests should verify the results this returns."
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (let [{{status# :status body# :body} :object} (ex-data e#)
             errors# (:errors (json/decode body# true))]
         {:status status# :errors errors#}))))