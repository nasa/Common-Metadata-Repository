(ns cmr.search.test.services.tagging-service
  (:require [clojure.test :refer :all]
            [cmr.search.services.tagging-service :as ts]
            [cmr.common.validations.core :as v]
            [cmr.search.services.tagging.tagging-service-messages :as msg]))

(def create-tag-validations (var-get #'ts/tag-validations))
(def update-tag-validations (var-get #'ts/update-tag-validations))

(def valid-tag
  {:namespace "foo" :value "bah"})

(defn is-valid
  [validation tag]
  (is (= {} (v/validate validation tag))))

(deftest tag-validations-test
  (testing "Create validations"
    (testing "valid create tag"
      (is-valid create-tag-validations valid-tag))

    (testing "invalid characters"
      (are [field]
           (= {[field] [msg/field-may-not-contain-separator]}
              (v/validate create-tag-validations (assoc valid-tag field (str "foo" (char 29)))))
           :namespace :value)))

  (testing "Update validations"
    (testing "Valid update"
      (is-valid
        update-tag-validations
        {:namespace "foo"
         :value "bah"
         :originator-id "help"
         :category "something original"
         :description "anything different"
         :existing {:namespace "foo"
                    :value "bah"
                    :originator-id "help"
                    :category "something"
                    :description "anything"}}))

    (testing "Namespace can't change"
      (is (= {[:namespace] [(v/field-cannot-be-changed-msg "current" "update")]}
             (v/validate
               update-tag-validations
               {:namespace "update" :value "foo"
                :existing {:namespace "current" :value "foo"}}))))

    (testing "Value can't change"
      (is (= {[:value] [(v/field-cannot-be-changed-msg "current" "update")]}
             (v/validate
               update-tag-validations
               {:namespace "foo" :value "update"
                :existing {:namespace "foo" :value "current"}}))))

    (testing "Originator id can't change"
      (is (= {[:originator-id] [(v/field-cannot-be-changed-msg "current" "update")]}
             (v/validate
               update-tag-validations
               {:namespace "foo" :value "fooer" :originator-id "update"
                :existing {:namespace "foo" :value "fooer" :originator-id "current"}}))))

    (testing "Originator id can be null"
      (is-valid
        update-tag-validations
        {:namespace "foo" :value "fooer"
         :existing {:namespace "foo" :value "fooer" :originator-id "current"}}))))