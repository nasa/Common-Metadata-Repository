(ns cmr.system-int-test.es-spatial-plugin.spatial-plugin-test
  "Tests general elastic spatial plugin behaviors necessary for successful spatial searches"
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.url-helper :as url]))

;; --- Configuration ---
(def es-url (url/elastic-root "elastic"))
(def index-name "test_spatial_index")

;; --- Helper Functions ---
(defn create-index! []
  (client/put (str es-url "/" index-name)
              {:content-type :json
               :throw-exceptions false
               :body (json/generate-string
                       {:mappings
                        {:properties
                         {:ords-info {:type "integer"}
                          :ords      {:type "integer"}}}})}))

(defn delete-index! []
  (client/delete (str es-url "/" index-name) {:throw-exceptions false}))

(defn index-doc! [id doc]
  (client/put (str es-url "/" index-name "/_doc/" id "?refresh=true")
              {:content-type :json
               :body (json/generate-string doc)}))

(defn run-spatial-query []
  (let [query {:query
               {:script
                {:script
                 {:source "spatial"
                  :lang "cmr_spatial"
                  :params {:ords-info "4,2"
                           :ords "1312823830,-122484730"}}}}}
        response (client/post (str es-url "/" index-name "/_search")
                              {:content-type :json
                               :accept :json
                               :as :json  ;; <-- This tells clj-http to automatically parse the response into a Clojure map with keyword keys!
                               :throw-exceptions false
                               :body (json/generate-string query)})]
    (:body response)))

;; --- Fixtures ---
(defn setup-teardown-fixture [f]
  (delete-index!)
  (create-index!)
  (f)
  (delete-index!))

(use-fixtures :each setup-teardown-fixture)

;; --- The Actual Tests ---

(deftest spatial-plugin-exact-match-test
  (testing "Plugin successfully matches an identical shape"
    ;; Index Document 1 (Valid matching data)
    (index-doc! "1" {:ords-info [4 2]
                     :ords [1312823830 -122484730]})

    (let [results (run-spatial-query)
          hits (get-in results [:hits :total :value])]
      ;; Assert no 500 errors occurred
      (is (not (:error results)))
      ;; Assert the document was found
      (is (= 1 hits)))))

(deftest spatial-plugin-missing-ords-test
  (testing "Plugin safely ignores documents with missing ords without crashing"
    ;; Index Document 2 (Missing ords array)
    (index-doc! "2" {:ords-info [4 2]})

    (let [results (run-spatial-query)
          hits (get-in results [:hits :total :value])]
      ;; Assert that the script didn't throw an index_out_of_bounds_exception
      (is (not (:error results)))
      ;; Assert that it safely returned 0 hits
      (is (= 0 hits)))))

(deftest spatial-plugin-empty-array-test
  (testing "Plugin safely ignores documents with empty arrays"
    ;; Index Document 3 (Empty ords array)
    (index-doc! "3" {:ords-info [4 2]
                     :ords []})

    (let [results (run-spatial-query)
          hits (get-in results [:hits :total :value])]
      (is (not (:error results)))
      (is (= 0 hits)))))