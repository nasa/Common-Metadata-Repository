(ns cmr.system-int-test.search.tagging.tag-crud-test
  "This tests the CMR Search API's tagging capabilities"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.tag-util :as tags]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def field-maxes
  "A map of fields to their max lengths"
  {:namespace 514
   :value 515
   :description 4000
   :category 1030})

(defn string-of-length
  "Creates a string of the specified length"
  [n]
  (str/join (repeat n "x")))

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

    (testing "Create tag with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (tags/create-tag valid-user-token valid-tag {:http-options {:content-type :xml}}))))

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
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format "/%s string \"%s\" is too long (length: %d, maximum allowed: %d)"
                                   (name field) long-value (inc max-length) max-length)]}
                 (tags/create-tag
                   valid-user-token
                   (assoc valid-tag field long-value)))))))

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
      (tags/assert-tag-saved (assoc tag :originator-id "user1") "user1" concept-id revision-id)

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
          (is (= 1 (:revision-id response)))))


      (testing "Creation of previously deleted tag"
        (tags/delete-tag token concept-id)
        (let [new-tag (assoc tag :category "new cat" :description "new description")
              token2 (e/login (s/context) "user2")
              response (tags/create-tag token2 new-tag)]
          (is (= {:status 200 :concept-id concept-id :revision-id 3}
                 response))
          ;; A tag that was deleted but recreated gets a new originator id.
          (tags/assert-tag-saved (assoc new-tag :originator-id "user2") "user2" concept-id 3))))

    (testing "Create tag with fields at maximum length"
      (let [tag (into {} (for [[field max-length] field-maxes]
                           [field (string-of-length max-length)]))]
        (is (= 200 (:status (tags/create-tag (e/login (s/context) "user1") tag)))))))

  (testing "Creation without required fields is allowed"
    (let [tag (dissoc (tags/make-tag 2) :category :description)
          token (e/login (s/context) "user1")
          {:keys [status concept-id revision-id]} (tags/create-tag token tag)]
      (is (= 200 status))
      (is concept-id)
      (is (= 1 revision-id)))))

(deftest get-tag-test
  (let [tag (tags/make-tag 1)
        token (e/login (s/context) "user1")
        {:keys [concept-id]} (tags/create-tag token tag)]
    (testing "Retrieve existing tag"
      (is (= {:status 200
              :body (assoc tag :originator-id "user1")
              :content-type :json}
             (tags/get-tag concept-id))))

    (testing "Retrieve unknown tag"
      (is (= {:status 404
              :body {:errors ["Tag could not be found with concept id [T100-CMR]"]}
              :content-type :json}
             (tags/get-tag "T100-CMR"))))
    (testing "Retrieve tag with bad concept-id"
      (is (= {:status 400
              :body {:errors ["Concept-id [F100-CMR] is not valid."]}
              :content-type :json}
             (tags/get-tag "F100-CMR"))))
    (testing "Retrieve tag with bad provider in concept id"
      (is (= {:status 400
              :body {:errors ["[T100-PROV1] is not a valid tag concept id."]}
              :content-type :json}
             (tags/get-tag "T100-PROV1"))))
    (testing "Retrieve tag with collection concept-id"
      (let [{coll-concept-id :concept-id} (d/ingest "PROV1" (dc/collection))]
        (is (= {:status 400
                :body {:errors [(format "[%s] is not a valid tag concept id." coll-concept-id)]}
                :content-type :json}
               (tags/get-tag coll-concept-id)))))
    (testing "Retrieve deleted tag"
      (tags/delete-tag token concept-id)
      (is (= {:status 404
              :content-type :json
              :body {:errors [(format "Tag with concept id [%s] was deleted." concept-id)]}}
             (tags/get-tag concept-id))))))


(deftest update-tag-test
  (let [tag (tags/make-tag 1)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)]

    (testing "Update with originator id"
      (let [updated-tag (-> tag
                            (update-in [:category] #(str % " updated"))
                            (update-in [:description] #(str % " updated"))
                            (assoc :originator-id "user1"))
            token2 (e/login (s/context) "user2")
            response (tags/update-tag token2 concept-id updated-tag)]
        (is (= {:status 200 :concept-id concept-id :revision-id 2}
               response))
        (tags/assert-tag-saved updated-tag "user2" concept-id 2)))

    (testing "Update without originator id"
      (let [updated-tag (dissoc tag :originator-id)
            token2 (e/login (s/context) "user2")
            response (tags/update-tag token2 concept-id updated-tag)]
        (is (= {:status 200 :concept-id concept-id :revision-id 3}
               response))
        ;; The previous originator id should not change
        (tags/assert-tag-saved (assoc updated-tag :originator-id "user1") "user2" concept-id 3)))))

(deftest update-tag-failure-test
  (let [tag (tags/make-tag 1)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)
        ;; The stored updated tag would have user1 in the originator id
        tag (assoc tag :originator-id "user1")]

    (testing "Update tag with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (tags/update-tag token concept-id tag {:http-options {:content-type :xml}}))))

    (testing "Update without token"
      (is (= {:status 401
              :errors ["Tags cannot be modified without a valid user token."]}
             (tags/update-tag nil concept-id tag))))

    (testing "Fields that cannot be changed"
      (are [field human-name]
           (= {:status 400
               :errors [(format (str "Tag %s cannot be modified. Attempted to change existing value"
                                     " [%s] to [updated]")
                                human-name
                                (get tag field))]}
              (tags/update-tag token concept-id (assoc tag field "updated")))
           :namespace "Namespace"
           :value "Value"
           :originator-id "Originator Id"))

    (testing "Updates applies JSON validations"
      (is (= {:status 400
              :errors ["/description string \"\" is too short (length: 0, required minimum: 1)"]}
             (tags/update-tag token concept-id (assoc tag :description "")))))

    (testing "Update tag that doesn't exist"
      (is (= {:status 404
              :errors ["Tag could not be found with concept id [T100-CMR]"]}
             (tags/update-tag token "T100-CMR" tag))))

    (testing "Update deleted tag"
      (tags/delete-tag token concept-id)
      (is (= {:status 404
              :errors [(format "Tag with concept id [%s] was deleted." concept-id)]}
             (tags/update-tag token concept-id tag))))))

(deftest delete-tag-test
  (let [tag (tags/make-tag 1)
        token (e/login (s/context) "user1")
        {:keys [concept-id revision-id]} (tags/create-tag token tag)]

    (testing "Delete without token"
      (is (= {:status 401
              :errors ["Tags cannot be modified without a valid user token."]}
             (tags/delete-tag nil concept-id))))

    (testing "Delete success"
      (is (= {:status 200 :concept-id concept-id :revision-id 2}
             (tags/delete-tag token concept-id)))
      (tags/assert-tag-deleted tag "user1" concept-id 2))

    (testing "Delete tag that was already deleted"
      (is (= {:status 404
              :errors [(format "Tag with concept id [%s] was deleted." concept-id)]}
             (tags/delete-tag token concept-id))))

    (testing "Delete tag that doesn't exist"
      (is (= {:status 404
              :errors ["Tag could not be found with concept id [T100-CMR]"]}
             (tags/delete-tag token "T100-CMR"))))))







