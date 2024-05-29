(ns cmr.ingest.test.api.generic-documents
  "tests functions in generic-documents"
  (:require [clojure.test :refer [deftest is testing]]
            [cmr.ingest.api.generic-documents :as gen-docs])
  (:import (clojure.lang ExceptionInfo)))

(deftest publish-draft-test
  (testing "content-type error condition"
    (let [request {:content-type "application/x-www-form-urlencoded"
                   :body ""}
          concept-id "CD1234-PROV1"
          native-id "native-id"]
        (is (thrown? ExceptionInfo (gen-docs/publish-draft request concept-id native-id)))))

  (testing "concept not a draft concept"
    (let [request {}
          concept-id "C1234-PROV1"
          native-id "native-id"]
        (is (thrown? ExceptionInfo (gen-docs/publish-draft request concept-id native-id)))))

  (testing "concept is a draft concept but not variable with association."
    (let [request {}
          concept-id "CD1234-PROV1"
          native-id "native-id"]
      (with-bindings {#'gen-docs/publish-draft-concept (fn [& _])
                      #'gen-docs/read-body (fn [& _])}
        (is (nil? (gen-docs/publish-draft request concept-id native-id))))))
  
  (testing "concept is a draft concept with variable with association."
    (let [request {}
          concept-id "VD1234-PROV1"
          native-id "native-id"]
      (with-bindings {#'gen-docs/publish-draft-concept (fn [& _])
                      #'gen-docs/read-body (fn [& _])}
        (is (nil? (gen-docs/publish-draft request concept-id native-id)))))))
