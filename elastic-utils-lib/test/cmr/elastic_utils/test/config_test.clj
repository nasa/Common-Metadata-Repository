(ns cmr.elastic-utils.test.config-test
  (:require
    [clojure.test :refer :all]
    [cmr.elastic-utils.config :as es-config]
    [cmr.common.util :refer [are3]]))

(deftest es-cluster-name-str-keyword-test
  (testing "cluster name successfully keywordized"
    (are3
      [es-cluster-name expected-cluster-name-keyword]
      (is (= expected-cluster-name-keyword (es-config/es-cluster-name-str->keyword es-cluster-name)))

      "Given correct non gran cluster name, Returns keywordized non gran cluster name"
      "elastic" :elastic

      "Given keywordized string of non gran cluster name, Returns keywordized non gran cluster name"
      :elastic :elastic

      "Given correct gran cluster name, Returns keywordized non gran cluster name"
      "gran-elastic" :gran-elastic

      "Given keywordized string of gran cluster name, Returns keywordized gran cluster name"
      :gran-elastic :gran-elastic))

  (testing "Given incorrect cluster name, throws error"
    (is (thrown? Exception (es-config/es-cluster-name-str->keyword "random")))))
