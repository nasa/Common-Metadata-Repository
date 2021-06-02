(ns cmr.system-int-test.misc.health-test
  "This tests the health api for CMR applications."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [clojure.string :as str]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.time-keeper :as tk]
   [cmr.elastic-utils.connect :as es-util]
   [cmr.search.routes :as routes]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.url-helper :as url]))

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
   :dependencies {:oracle {:ok? true}}})

(def good-indexer-health
  {:ok? true
   :dependencies {:elastic_search {:ok? true}
                  :echo {:ok? true}
                  :message-queue {:ok? true}
                  :metadata-db good-metadata-db-health}})

(def good-ingest-health
  {:ok? true
   :dependencies {:oracle {:ok? true}
                  :echo {:ok? true}
                  :metadata-db good-metadata-db-health
                  :message-queue {:ok? true}
                  :indexer good-indexer-health}})

(deftest robots-dot-txt-test
  (let [_ (side/eval-form `(routes/set-test-environment! false))
        robots (client/get "http://localhost:3003/robots.txt"
                           {:accept :text
                            :connection-manager (s/conn-mgr)})
        body (str/split-lines (:body robots))]
   (is (= "User-agent: *" (first body)))
   (is (= "Disallow: /ingest/health" (second body)))))

(deftest test-env-robots-dot-txt-test
  (let [_ (side/eval-form `(routes/set-test-environment! true))
        robots (client/get "http://localhost:3003/robots.txt"
                           {:accept :text
                            :connection-manager (s/conn-mgr)})
        body (str/split-lines (:body robots))]
   (is (= "User-agent: *" (first body)))
   (is (= "Disallow: /" (second body)))))

(deftest ^:oracle metadata-db-health-test
  (s/only-with-real-database
    (is (= [200 {:oracle {:ok? true}}]
           (get-app-health (url/mdb-health-url))))))

(deftest ^:oracle indexer-health-test
  (s/only-with-real-database
    (is (= [200 {:elastic_search {:ok? true}
                 :echo {:ok? true}
                 :message-queue {:ok? true}
                 :metadata-db good-metadata-db-health}]
           (get-app-health (url/indexer-health-url))))))

(deftest ^:oracle ingest-health-test
  (s/only-with-real-database
    (is (= [200 {:oracle {:ok? true}
                 :echo {:ok? true}
                 :metadata-db good-metadata-db-health
                 :message-queue {:ok? true}
                 :indexer good-indexer-health}]
           (get-app-health (url/ingest-health-url))))))

(deftest ^:oracle search-health-test
  (s/only-with-real-database
    (is (= [200 {:echo {:ok? true}
                 :internal-metadata-db good-metadata-db-health
                 :indexer good-indexer-health}]
           (get-app-health (url/search-health-url))))))

(deftest ^:oracle bootstrap-health-test
  (s/only-with-real-database
    (is (= [200 {:metadata-db good-metadata-db-health
                 :internal-metadata-db good-metadata-db-health
                 :indexer (update-in good-indexer-health [:dependencies] dissoc :message-queue)}]
           (get-app-health (url/bootstrap-health-url))))))

(deftest ^:oracle virtual-product-health-test
  (s/only-with-real-database
    (is (= [200 {:ingest good-ingest-health
                 :metadata-db good-metadata-db-health
                 :message-queue {:ok? true}}]
           (get-app-health (url/virtual-product-health-url))))))

(deftest ^:oracle access-control-health-test
  (s/only-with-real-database
    (is (= [200 {:echo {:ok? true}
                 :metadata-db good-metadata-db-health}]
           (get-app-health (url/access-control-health-url))))))
