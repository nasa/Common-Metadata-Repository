(ns cmr.system-int-test.health-test
  "This tests the index-set health api."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.utils.test-environment :as test-env]))

(deftest index-set-health-test
  (testing "index set good health"
    (let [response (client/get (url/index-set-health-url)
                               {:accept :json
                                :throw-exceptions false
                                :connection-manager (url/conn-mgr)})
          status-code (:status response)
          health-detail (json/decode (:body response) true)]

      (is (= [200 {:elastic_search {:ok? true}, :echo {:ok? true}}]
             [status-code health-detail])))))

(deftest metadata-db-health-test
  (testing "metadata db good health"
    (test-env/only-with-real-database
      (let [response (client/get (url/mdb-health-url)
                                 {:accept :json
                                  :throw-exceptions false
                                  :connection-manager (url/conn-mgr)})
            status-code (:status response)
            health-detail (json/decode (:body response) true)]

        (is (= [200 {:oracle {:ok? true}, :echo {:ok? true}}]
               [status-code health-detail]))))))

(deftest indexer-health-test
  (testing "indexer good health"
    (test-env/only-with-real-database
      (let [response (client/get (url/indexer-health-url)
                                 {:accept :json
                                  :throw-exceptions false
                                  :connection-manager (url/conn-mgr)})
            status-code (:status response)
            health-detail (json/decode (:body response) true)]

        (is (= [200 {:elastic_search {:ok? true} :echo {:ok? true}
                     :metadata-db {:ok? true
                                   :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                     :index-set {:ok? true
                                 :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}}}]
               [status-code health-detail]))))))

(deftest ingest-health-test
  (testing "ingest good health"
    (test-env/only-with-real-database
      (let [response (client/get (url/ingest-health-url)
                                 {:accept :json
                                  :throw-exceptions false
                                  :connection-manager (url/conn-mgr)})
            status-code (:status response)
            health-detail (json/decode (:body response) true)]

        (is (= [200 {:oracle {:ok? true} :echo {:ok? true}
                     :metadata-db {:ok? true
                                   :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                     :indexer
                     {:ok? true
                      :dependencies
                      {:elastic_search {:ok? true} :echo {:ok? true}
                       :metadata-db {:ok? true :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                       :index-set
                       {:ok? true :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}}}}}]
               [status-code health-detail]))))))

(deftest search-health-test
  (testing "search good health"
    (test-env/only-with-real-database
      (let [response (client/get (url/search-health-url)
                                 {:accept :json
                                  :throw-exceptions false
                                  :connection-manager (url/conn-mgr)})
            status-code (:status response)
            health-detail (json/decode (:body response) true)]

        (is (= [200 {:echo {:ok? true}
                     :metadata-db {:ok? true, :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                     :index-set {:ok? true
                                 :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}}}]
               [status-code health-detail]))))))

(deftest bootstrap-health-test
  (testing "bootstrap good health"
    (test-env/only-with-real-database
      (let [response (client/get (url/bootstrap-health-url)
                                 {:accept :json
                                  :throw-exceptions false
                                  :connection-manager (url/conn-mgr)})
            status-code (:status response)
            health-detail (json/decode (:body response) true)]

        (is (= [200
                {:metadata-db {:ok? true, :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                 :internal-meta-db {:ok? true, :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                 :indexer
                 {:ok? true
                  :dependencies
                  {:elastic_search {:ok? true} :echo {:ok? true}
                   :metadata-db {:ok? true
                                 :dependencies {:oracle {:ok? true}, :echo {:ok? true}}}
                   :index-set {:ok? true
                               :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}}}}}]
               [status-code health-detail]))))))
