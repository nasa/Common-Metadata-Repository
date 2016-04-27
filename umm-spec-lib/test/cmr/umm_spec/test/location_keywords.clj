(ns cmr.umm-spec.test.location-keywords
  (:require [clojure.umm-spec.location-keywords :as lk]))

(deftest generate-and-parse-umm-s-json
  (testing "minimal umm-s record"
    (let [json (uj/umm->json minimal-example-umm-s-record)
          _ (is (empty? (js/validate-umm-json json :service)))
          parsed (uj/json->umm :service json)]
      (is (= minimal-example-umm-s-record parsed)))))
