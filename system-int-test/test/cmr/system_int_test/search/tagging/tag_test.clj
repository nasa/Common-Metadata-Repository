(ns cmr.system-int-test.search.tagging.tag-test
  "This tests the CMR Search API's tagging capabilities"
  (:require [clojure.test :refer :all]
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


(deftest create-tag-test
  ;; TODO Verify tag with namespace or value with group separator is rejected

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
        ;; TODO implement this
        )))

  (testing "Create without token"
    (is (= {:status 401
            :errors ["Tags cannot be modified without a valid user token."]}
           (tags/create-tag nil (tags/make-tag 1)))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token ABC does not exist"]}
           (tags/create-tag "ABC" (tags/make-tag 1))))))

; (mdb/get-concept "T1200000000-CMR" )

;; TODO tests
;; tags without required fields
;; Tags that aren't JSON

;; Later TODOs
;; TODO test updating a tag doesn't change the originator