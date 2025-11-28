(ns cmr.elastic-utils.test.config-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [cmr.elastic-utils.config :as es-config]
    [cmr.common.util :refer [are3]]))

(deftest es-cluster-name-str-keyword-test
  (testing "cluster name successfully keywordized"
    (are3
      [es-cluster-name expected-cluster-name-keyword]
      (is (= expected-cluster-name-keyword (es-config/elastic-name-str->keyword es-cluster-name)))

      "Given correct non gran cluster name, Returns keywordized non gran cluster name"
      "elastic"
      :elastic

      "Given keywordized string of non gran cluster name, Returns keywordized non gran cluster name"
      :elastic
      :elastic

      "Given correct gran cluster name, Returns keywordized non gran cluster name"
      "gran-elastic"
      :gran-elastic

      "Given keywordized string of gran cluster name, Returns keywordized gran cluster name"
      :gran-elastic
      :gran-elastic))

  (testing "Given incorrect cluster name, throws error"
    (is (thrown? Exception (es-config/elastic-name-str->keyword "random")))))

(deftest test-validate-elastic-name
  (testing "validate correct elastic names"
    (are3 [elastic-name]
          (is (= nil (es-config/validate-elastic-name elastic-name)))

          "Given gran elastic name, will return nil"
          "gran-elastic"

          "Given elastic name, will return nil"
          "elastic"))
  (testing "Given incorrect elastic name, will throw errors"
    (are3 [elastic-name]
          (is (thrown? Exception (es-config/validate-elastic-name elastic-name)))

          "Given blank elastic name, will throw service error"
          ""

          "Given random name, will throw service error"
          "random"

          "Given nil name, will throw service error"
          nil)))