(ns cmr.common-app.test.api.health
  "Unit tests for health checks"
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common-app.api.health :as health]
   [cmr.common.lifecycle :as lifecycle]))

(def get-app-health
  "Allow testing private get-app-health function"
  #'health/get-app-health)

(defn- get-context-with-health-cache
  "Context for the test that includes a health cache"
  []
  {:system {:caches {health/health-cache-key (lifecycle/start (health/create-health-cache) nil)}}})

(defn- healthy-fn
  [context]
  {:ok? true
   :dependencies {:test-resource {:ok? true}}})

(defn- unhealthy-fn
  [context]
  {:ok? false
   :dependencies {:test-resource {:ok? false :problem "Resource is not responding."}}})

(deftest application-health-uses-cache-test
  (let [healthy-response {:status 200
                          :body (json/generate-string {:test-resource {:ok? true}})}
        unhealthy-response {:status 503
                            :body (json/generate-string
                                   {:test-resource {:ok? false
                                                    :problem "Resource is not responding."}})}
        context-with-health-cache (get-context-with-health-cache)]
    (is (= healthy-response
           (select-keys (get-app-health context-with-health-cache healthy-fn) [:status :body])))
    (testing "health response is cached so even though the unhealthy-fn is passed in it is not used"
      (is (= healthy-response
             (select-keys (get-app-health context-with-health-cache unhealthy-fn)
                          [:status :body])))
      ;; Wait 2 seconds and the cache has not expired, so it still returns healthy
      (Thread/sleep 2001)
      (is (= healthy-response
             (select-keys (get-app-health context-with-health-cache unhealthy-fn)
                          [:status :body]))))

    ;; Wait another 3 seconds for a total of 5 and the cache has expired
    (Thread/sleep 3001)
    (testing "After the cache TTL has expired, the health function will be called"
      (is (= unhealthy-response
             (select-keys (get-app-health context-with-health-cache unhealthy-fn)
                          [:status :body]))))

    (testing "Without a cache the health function is always called"
      (is (= healthy-response (select-keys (get-app-health nil healthy-fn) [:status :body])))
      (is (= unhealthy-response (select-keys (get-app-health nil unhealthy-fn) [:status :body]))))))
