;; (ns cmr.indexer.test.data.concepts.collection.kms-util
;;   "This namespace conducts unit tests on the project-keyword namespace."
;;   (:require
;;    [clojure.test :refer [deftest is join-fixtures use-fixtures testing]]
;;    [cmr.common-app.services.kms-lookup :as kms-lookup]
;;    [cmr.indexer.data.concepts.collection.kms_util:as kms-util]
;;    [cmr.redis-utils.test.test-util :as redis-embedded-fixture]))

;; (def ^:private sample-keyword-map
;;   {:projects [{:short-name "KMS-SHORT" :long-name "KMS-LONG" :uuid "kms-uuid-123"},{:short-name "KMS-SHORT2" :long-name "KMS-LONG2"}]})

;; (def create-context
;;   {:system {:caches {kms-lookup/kms-short-name-cache-key (kms-lookup/create-kms-short-name-cache)
;;                      kms-lookup/kms-projects-cache-key   (kms-lookup/create-kms-project-uuid-cache)
;;                      kms-lookup/kms-umm-c-cache-key      (kms-lookup/create-kms-umm-c-cache)
;;                      kms-lookup/kms-location-cache-key   (kms-lookup/create-kms-location-cache)
;;                      kms-lookup/kms-measurement-cache-key (kms-lookup/create-kms-measurement-cache)}}})

;; (defn redis-cache-fixture
;;   [f]
;;   (let [context create-context]
;;     (kms-lookup/create-kms-index context sample-keyword-map)
;;     (f)))

;; (use-fixtures :once (join-fixtures [redis-embedded-fixture/embedded-redis-server-fixture
;;                                     redis-cache-fixture]))

;; (deftest project-short-name->elastic-doc-test
;;   (let [context create-context]
;;     (testing "Project found in KMS"
;;       (is (= {:uuid "kms-uuid-123"}
;;              (kms-util/project-short-name->elastic-doc context "KMS-SHORT"))))

;;     (testing "Project not found in KMS"
;;       (is (nil?
;;            (kms-util/project-short-name->elastic-doc context "NOT-IN-KMS"))))
    
;;      (testing "no uuid is found in the keyword cache"
;;        (is (= {:uuid "kms-uuid-123"}
;;             (kms-util/project-short-name->elastic-doc context "KMS-SHORT2"))))))

