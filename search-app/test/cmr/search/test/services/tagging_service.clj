(ns cmr.search.test.services.tagging-service
  (:require [clojure.test :refer :all]
            [cmr.search.services.tagging-service :as ts]
            [cmr.common.validations.core :as v]
            [cmr.search.services.tagging.tagging-service-messages :as msg]))

(def update-tag-validations (var-get #'ts/update-tag-validations))

(defn is-valid
  [validation tag]
  (is (= {} (v/validate validation tag))))

(deftest tag-validations-test
  (testing "Update validations"
    (testing "Valid update"
      (is-valid
        update-tag-validations
        {:tag-key "foo"
         :originator-id "help"
         :description "anything different"
         :existing {:tag-key "foo"
                    :originator-id "help"
                    :description "anything"}}))

    (testing "tag-key can't change"
      (is (= {[:tag-key] [(v/field-cannot-be-changed-msg "current" "update")]}
             (v/validate
               update-tag-validations
               {:tag-key "update"
                :existing {:tag-key "current"}}))))

    (testing "Originator id can't change"
      (is (= {[:originator-id] [(v/field-cannot-be-changed-msg "current" "update")]}
             (v/validate
               update-tag-validations
               {:tag-key "foo" :originator-id "update"
                :existing {:tag-key "foo" :originator-id "current"}}))))

    (testing "Originator id can be null"
      (is-valid
        update-tag-validations
        {:tag-key "foo"
         :existing {:tag-key "foo" :originator-id "current"}}))))