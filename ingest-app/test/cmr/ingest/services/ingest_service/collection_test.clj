(ns cmr.ingest.services.ingest-service.collection-test
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.services.ingest-service.collection :as collection]))

(deftest should-notify-kms?-test
  (testing "When has-keyword-error? is true and existing-errors are present, Then it returns true"
    (is (true? (collection/should-notify-kms? true ["some error"] []))))

  (testing "When has-keyword-error? is true and warnings are present, Then it returns true"
    (is (true? (collection/should-notify-kms? true [] ["some warning"]))))

  (testing "When has-keyword-error? is false, Then it returns false regardless of errors or warnings"
    (is (not (collection/should-notify-kms? false ["some error"] ["some warning"]))))

  (testing "When has-keyword-error? is true but no errors or warnings exist, Then it returns false"
    (is (not (collection/should-notify-kms? true [] [])))))

