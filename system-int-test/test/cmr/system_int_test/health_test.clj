(ns cmr.system-int-test.health-test
  "This tests the health api for CMR applications."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.system :as s]))

(defn- get-app-health
  "Returns the status code and health detail of the app with the given health endpoint url"
  [app-health-url]
  (let [response (client/get app-health-url
                             {:accept :json
                              :throw-exceptions false
                              :connection-manager (s/conn-mgr)})
        status-code (:status response)
        health-detail (json/decode (:body response) true)]
    [status-code health-detail]))

(def good-metadata-db-health
  {:ok? true
   :dependencies {:oracle {:ok? true}, :echo {:ok? true}}})

(def good-index-set-db-health
  {:ok? true
   :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}})

(deftest index-set-health-test
  (is (= [200 {:elastic_search {:ok? true} :echo {:ok? true}}]
         (get-app-health (url/index-set-health-url)))))

(deftest cubby-health-test
  (is (= [200 {:elastic_search {:ok? true} :echo {:ok? true}}]
         (get-app-health (url/cubby-health-url)))))

(deftest metadata-db-health-test
  (s/only-with-real-database
    (is (= [200 {:oracle {:ok? true} :echo {:ok? true}}]
           (get-app-health (url/mdb-health-url))))))

(deftest indexer-health-test
  (s/only-with-real-database
    (is (= [200 {:elastic_search {:ok? true}
                 :echo {:ok? true}
                 :metadata-db good-metadata-db-health
                 :index-set good-index-set-db-health}]
           (get-app-health (url/indexer-health-url))))))

(deftest ingest-health-test
  (s/only-with-real-database
    (is (= [200 {:oracle {:ok? true}
                 :echo {:ok? true}
                 :metadata-db good-metadata-db-health
                 :indexer {:ok? true
                           :dependencies {:elastic_search {:ok? true}
                                          :echo {:ok? true}
                                          :metadata-db good-metadata-db-health
                                          :index-set good-index-set-db-health}}}]
           (get-app-health (url/ingest-health-url))))))

(deftest search-health-test
  (s/only-with-real-database
    (is (= [200 {:echo {:ok? true}
                 :internal-metadata-db good-metadata-db-health
                 :index-set good-index-set-db-health}]
           (get-app-health (url/search-health-url))))))

(deftest bootstrap-health-test
  (s/only-with-real-database
    (is (= [200 {:metadata-db good-metadata-db-health
                 :internal-metadata-db good-metadata-db-health
                 :indexer {:ok? true
                           :dependencies {:elastic_search {:ok? true}
                                          :echo {:ok? true}
                                          :metadata-db good-metadata-db-health
                                          :index-set good-index-set-db-health}}}]
           (get-app-health (url/bootstrap-health-url))))))
