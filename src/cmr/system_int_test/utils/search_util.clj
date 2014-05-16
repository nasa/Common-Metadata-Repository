(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.concepts :as cs]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.util :as u]
            [camel-snake-kebab :as csk]
            [clojure.walk]))

(defn params->snake_case
  "Converts search parameters to snake_case"
  [params]
  (->> params
       (u/map-keys
         (fn [k]
           (let [k (if (keyword? k) (name k) k)]
             (-> k
                 csk/->snake_case
                 (str/replace "_[" "[")
                 (str/replace "_]" "]")))))
       clojure.walk/keywordize-keys))

(deftest params->snake_case-test
  (is (= {(keyword "archive_center[]") ["SEDAC AC" "Larc" "Sedac AC"],
          (keyword "options[archive_center][and]") "false"
          :foo_bar "chew"}
         (params->snake_case
           {"archive-center[]" ["SEDAC AC" "Larc" "Sedac AC"],
            "options[archive-center][and]" "false"
            :foo-bar "chew"}))))

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

(defn find-refs-with-embedded-params
  "Returns the references that are found by searching with a given param string."
  [concept-type params]
  (get-search-failure-data
    (let [response (client/get (str (url/search-url concept-type) "?" params)
                               {:accept :json})
          _ (is (= 200 (:status response)))
          result (json/decode (:body response) true)]
      (-> result
          ;; Rename references to refs
          (assoc :refs (:references result))
          (dissoc :references)))))

(defn find-refs
  "Returns the references that are found by searching with the input params"
  [concept-type params]
  (get-search-failure-data
    (let [params (u/map-keys csk/->snake_case_keyword params)
          response (client/get (url/search-url concept-type)
                               {:accept :json
                                :query-params (params->snake_case params)})
          _ (is (= 200 (:status response)))
          result (json/decode (:body response) true)]
      (-> result
          ;; Rename references to refs
          (assoc :refs (:references result))
          (dissoc :references)))))

(defn get-concept-by-concept-id
  "Returns the concept metadata by searching metadata-db using the given cmr concept id"
  [concept-id]
  (let [concept-type (cs/concept-prefix->concept-type (subs concept-id 0 1))]
    (client/get (url/retrieve-concept-url concept-type concept-id)
                {:throw-exceptions false})))
