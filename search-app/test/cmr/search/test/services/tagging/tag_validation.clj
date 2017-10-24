(ns cmr.search.test.services.tagging.tag-validation
  (:require [clojure.test :refer :all]
            [cmr.search.services.tagging.tag-validation :as tv]
            [cmr.common.validations.core :as v]
            [cmr.common.validations.messages :as v-msg]
            [cmr.search.services.tagging.tagging-service-messages :as msg]))

(def update-tag-validations (var-get #'tv/update-tag-validations))

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
      (is (= {[:tag-key] [(v-msg/field-cannot-be-changed "current" "update")]}
             (v/validate
               update-tag-validations
               {:tag-key "update"
                :existing {:tag-key "current"}}))))

    (testing "Originator id can't change"
      (is (= {[:originator-id] [(v-msg/field-cannot-be-changed "current" "update")]}
             (v/validate
               update-tag-validations
               {:tag-key "foo" :originator-id "update"
                :existing {:tag-key "foo" :originator-id "current"}}))))

    (testing "Originator id can be null"
      (is-valid
        update-tag-validations
        {:tag-key "foo"
         :existing {:tag-key "foo" :originator-id "current"}}))))
