(ns cmr.ingest.services.ingest-service.collection-test
  (:require
   [clojure.test :refer :all]
   [cmr.ingest.services.ingest-service.collection :as collection]))

(deftest should-notify-kms?-test
  (testing "true when has-keyword-error? and existing-errors are present"
    (is (true? (collection/should-notify-kms? true ["some error"] []))))

  (testing "true when has-keyword-error? and warnings are present"
    (is (true? (collection/should-notify-kms? true [] ["some warning"]))))

  (testing "false when has-keyword-error? is false, regardless of errors/warnings"
    (is (not (collection/should-notify-kms? false ["some error"] ["some warning"]))))

  (testing "false when has-keyword-error? is true but no errors or warnings exist"
    (is (not (collection/should-notify-kms? true [] [])))))
