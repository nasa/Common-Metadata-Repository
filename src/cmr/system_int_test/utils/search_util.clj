(ns ^{:doc "provides search related utilities."}
  cmr.system-int-test.utils.search-util
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.concepts :as cs]
            [cmr.common.mime-types :as mime-types]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.util :as u]
            [camel-snake-kebab :as csk]
            [clojure.set :as set]
            [clojure.walk]
            [ring.util.codec :as codec]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]))

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

(defn find-concepts-in-format
  "Returns the concepts in the format given."
  [format concept-type params & no-snake-kebab]
  ;; no-snake-kebab needed for legacy psa which use camel case minValue/maxValue

  (let [params (if no-snake-kebab
                 params
                 (u/map-keys csk/->snake_case_keyword params))
        response (client/get (url/search-url concept-type)
                             {:accept format
                              :query-params (if no-snake-kebab
                                              params
                                              (params->snake_case params))
                              :connection-manager (url/conn-mgr)})]
    (is (= 200 (:status response)))
    response))

(defn find-grans-csv
  "Returns the response of granule search in csv format"
  [concept-type params & no-snake-kebab]
  (get-search-failure-data
    (find-concepts-in-format "text/csv" concept-type params no-snake-kebab)))

(defn find-metadata
  "Returns the response of concept search in a specific metadata XML format."
  [concept-type format-key params & no-snake-kebab]
  (get-search-failure-data
    (let [format-mime-type (mime-types/format->mime-type format-key)
          response (find-concepts-in-format format-mime-type concept-type params no-snake-kebab)
          parsed (x/parse-str (:body response))]
      (map (fn [result]
             (let [{attrs :attrs [inner-elem] :content} result
                   {:keys [concept-id collection-concept-id revision-id]} attrs]
               {:concept-id concept-id
                :revision-id (Long. ^String revision-id)
                :format format-key
                :collection-concept-id collection-concept-id
                :metadata (x/emit-str inner-elem)}))
           (cx/elements-at-path parsed [:result])))))

(defn find-refs
  "Returns the references that are found by searching with the input params"
  [concept-type params & no-snake-kebab]
  (get-search-failure-data
    (-> (find-concepts-in-format :json concept-type params no-snake-kebab)
        :body
        (json/decode true)
        (set/rename-keys {:references :refs}))))

(defn find-refs-with-post
  "Returns the references that are found by searching through POST request with the input params"
  [concept-type params]
  (get-search-failure-data
    (let [response (client/post (url/search-url concept-type)
                                {:accept :json
                                 :content-type "application/x-www-form-urlencoded"
                                 :body (codec/form-encode params)
                                 :throw-exceptions false
                                 :connection-manager (url/conn-mgr)})]
      (is (= 200 (:status response)))
      (set/rename-keys (json/decode (:body response) true)
                       {:references :refs}))))

(defn get-concept-by-concept-id
  "Returns the concept metadata by searching metadata-db using the given cmr concept id"
  [concept-id]
  (let [concept-type (cs/concept-prefix->concept-type (subs concept-id 0 1))]
    (client/get (url/retrieve-concept-url concept-type concept-id)
                {:throw-exceptions false
                 :connection-manager (url/conn-mgr)})))
