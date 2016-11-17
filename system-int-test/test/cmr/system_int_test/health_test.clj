(ns cmr.system-int-test.health-test
  "This tests the health api for CMR applications."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.time-keeper :as tk]
   [cmr.elastic-utils.connect :as es-util]
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
   :dependencies {:oracle {:ok? true}, :echo {:ok? true}}})

(def good-index-set-db-health
  {:ok? true
   :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}})

(def good-cubby-health
  {:ok? true
   :dependencies {:elastic_search {:ok? true}, :echo {:ok? true}}})

(def good-indexer-health
  {:ok? true
   :dependencies {:elastic_search {:ok? true}
                  :echo {:ok? true}
                  :cubby good-cubby-health
                  :message-queue {:ok? true}
                  :metadata-db good-metadata-db-health
                  :index-set good-index-set-db-health}})

(def good-ingest-health
  {:ok? true
   :dependencies {:oracle {:ok? true}
                  :echo {:ok? true}
                  :metadata-db good-metadata-db-health
                  :message-queue {:ok? true}
                  :cubby good-cubby-health
                  :indexer good-indexer-health}})

(deftest robots-dot-txt-test
  (let [robots (slurp (io/resource "public/robots.txt"))]
   ;; Don't verify the contents of the file, as it's likely to change
   (is (not= nil robots))))

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
                 :message-queue {:ok? true}
                 :metadata-db good-metadata-db-health
                 :index-set good-index-set-db-health
                 :cubby good-cubby-health}]
           (get-app-health (url/indexer-health-url))))))

(deftest ingest-health-test
  (s/only-with-real-database
    (is (= [200 {:oracle {:ok? true}
                 :echo {:ok? true}
                 :metadata-db good-metadata-db-health
                 :message-queue {:ok? true}
                 :cubby good-cubby-health
                 :indexer good-indexer-health}]
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
                 :indexer (update-in good-indexer-health [:dependencies] dissoc :message-queue)}]
           (get-app-health (url/bootstrap-health-url))))))

(deftest virtual-product-health-test
  (s/only-with-real-database
    (is (= [200 {:ingest good-ingest-health
                 :metadata-db good-metadata-db-health
                 :message-queue {:ok? true}}]
           (get-app-health (url/virtual-product-health-url))))))

(deftest access-control-health-test
  (s/only-with-real-database
    (is (= [200 {:echo {:ok? true}
                 :metadata-db good-metadata-db-health}]
           (get-app-health (url/access-control-health-url))))))
