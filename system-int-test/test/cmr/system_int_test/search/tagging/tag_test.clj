(ns cmr.system-int-test.search.tagging.tag-test
  "This tests the CMR Search API's tagging capabilities"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.common.mime-types :as mt]
            [cheshire.core :as json]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest create-tag-validation-test
  (testing "Create without token"
    (is (= {:status 401
            :errors ["Tags cannot be modified without a valid user token."]}
           (tags/create-tag nil (tags/make-tag 1)))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token ABC does not exist"]}
           (tags/create-tag "ABC" (tags/make-tag 1)))))

  (let [valid-user-token (e/login (s/context) "user1")
        valid-tag (tags/make-tag 1)]
    (testing "Missing field validations"
      (are [field]
           (= {:status 400
               :errors [(format "object has missing required properties ([\"%s\"])" (name field))]}
              (tags/create-tag valid-user-token (dissoc valid-tag field)))

           :namespace :value))

    (testing "Minimum field length validations"
      (are [field]
           (= {:status 400
               :errors [(format "/%s string \"\" is too short (length: 0, required minimum: 1)"
                                (name field))]}
              (tags/create-tag valid-user-token (assoc valid-tag field "")))

           :namespace :value :description :category))

    (testing "Maximum field length validations"
      (are [field max-length]
           (let [long-value (str/join (repeat (inc max-length) "x"))]
             (= {:status 400
                 :errors [(format "/%s string \"%s\" is too long (length: %d, maximum allowed: %d)"
                                  (name field) long-value (inc max-length) max-length)]}
                (tags/create-tag
                  valid-user-token
                  (assoc valid-tag field long-value))))

           :namespace 514
           :value 515
           :description 4000
           :category 1030))

    (testing "Invalid namespace and value characters"
      (are [field field-name]
           (= {:status 400
               :errors [(str field-name " may not contain the Group Separator character. "
                                          "ASCII decimal value: 29 Unicode: U+001D")]}
              (tags/create-tag
                valid-user-token
                (assoc valid-tag field (str "abc" (char 29) "abc"))))
           :namespace "Namespace"
           :value "Value"))))


(deftest create-tag-test
  (testing "Successful creation"
    (let [tag (tags/make-tag 1)
          token (e/login (s/context) "user1")
          {:keys [status concept-id revision-id]} (tags/create-tag token tag)]
      (is (= 200 status))
      (is concept-id)
      (is (= 1 revision-id))

      (testing "Tag is persisted in metadata db"
        (let [concept (mdb/get-concept concept-id revision-id)]
          (is (= {:concept-type :tag
                  :native-id (str (:namespace tag) (char 29) (:value tag))
                  ;; TODO Get James or change it yourself that provider id shouldn't be returned if we don't send it in
                  :provider-id "CMR"
                  :format mt/edn
                  :metadata (pr-str (assoc tag :originator-id "user1"))
                  :user-id "user1"
                  :deleted false
                  :concept-id concept-id
                  :revision-id revision-id}
                 (dissoc concept :revision-date)))))

      (testing "Creation with an already existing namespace and value"
        (is (= {:status 409
                :errors [(format "A tag with namespace [%s] and value [%s] already exists with concept id %s."
                                 (:namespace tag) (:value tag) concept-id)]}
               (tags/create-tag token tag))))

      (testing "Creation with different namespace and same value succeeds"
        (let [response (tags/create-tag token (assoc tag :namespace "different"))]
          (is (= 200 (:status response)))
          (is (not= concept-id (:concept-id response)))
          (is (= 1 (:revision-id response)))))

      (testing "Creation with same namespace and different value succeeds"
        (let [response (tags/create-tag token (assoc tag :value "different"))]
          (is (= 200 (:status response)))
          (is (not= concept-id (:concept-id response)))
          (is (= 1 (:revision-id response)))))))

  (testing "Creation without required fields is allowed"
    (let [tag (dissoc (tags/make-tag 2) :category :description)
          token (e/login (s/context) "user1")
          {:keys [status concept-id revision-id]} (tags/create-tag token tag)]
      (is (= 200 status))
      (is concept-id)
      (is (= 1 revision-id)))))

;; Later TODOs
;; TODO test updating a tag doesn't change the originator
;; TODO test that creation of a tag that already exists by namespace and value but was deleted works
;; - it should get the same concept id