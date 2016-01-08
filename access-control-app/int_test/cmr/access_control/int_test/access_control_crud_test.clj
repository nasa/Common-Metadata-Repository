(ns cmr.access-control.int-test.access-control-crud-test
    (:require [clojure.test :refer :all]
              [clojure.string :as str]
              [cmr.mock-echo.client.echo-util :as e]
              [cmr.access-control.int-test.access-control-test-util :as u]))

(use-fixtures :once (u/int-test-fixtures))
(use-fixtures :each u/reset-fixture)

;; TODO test groups are unique by name and provider id

;; TODO CMR-2134, CMR-2133 test creating groups without various permissions

(def field-maxes
  "A map of fields to their max lengths"
  {:name 100
   :description 255
   :legacy-guid 50})

(defn string-of-length
  "Creates a string of the specified length"
  [n]
  (str/join (repeat n "x")))

(deftest create-group-validation-test
  (let [valid-user-token (e/login (u/conn-context) "user1")
        valid-group (u/make-group)]

    (testing "Create group with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (u/create-group valid-user-token valid-group {:http-options {:content-type :xml}}))))

    (testing "Missing field validations"
      (are [field]
        (= {:status 400
            :errors [(format "object has missing required properties ([\"%s\"])" (name field))]}
           (u/create-group valid-user-token (dissoc valid-group field)))

        :name :description))

    (testing "Minimum field length validations"
      (are [field]
        (= {:status 400
            :errors [(format "/%s string \"\" is too short (length: 0, required minimum: 1)"
                             (name field))]}
           (u/create-group valid-user-token (assoc valid-group field "")))

        :name :description :provider-id :legacy-guid))

    (testing "Maximum field length validations"
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format "/%s string \"%s\" is too long (length: %d, maximum allowed: %d)"
                                   (name field) long-value (inc max-length) max-length)]}
                 (u/create-group
                  valid-user-token
                  (assoc valid-group field long-value)))))))))

(deftest create-group-test
  (testing "Successful creation"
    (let [group (u/make-group)
          token (e/login (u/conn-context) "user1")
          {:keys [status concept-id revision-id]} (u/create-group token group)]
      (is (= 200 status))
      (is concept-id)
      (is (= 1 revision-id))
      (u/assert-group-saved group "user1" concept-id revision-id)

      (testing "Creation with an already existing name"
        ;; TODO make this same test for providers
        ;; TODO Creating a system level group and a provider level group with the same name is ok.
        (is (= {:status 409
                :errors [(format "A system group with name [%s] already exists with concept id %s."
                                 (:name group) concept-id)]}
               (u/create-group token group))))

      ;; TODO uncomment when implementing delete
      #_(testing "Creation of previously deleted group"
          (u/delete-group token concept-id)
          (let [new-group (assoc group :legacy-guid "the legacy guid" :description "new description")
                response (u/create-group token new-group)]
            (is (= {:status 200 :concept-id concept-id :revision-id 3}
                   response))
            (u/assert-group-saved new-group "user2" concept-id 3))))

    (testing "Create group with fields at maximum length"
      (let [group (into {} (for [[field max-length] field-maxes]
                             [field (string-of-length max-length)]))]
        (is (= 200 (:status (u/create-group (e/login (u/conn-context) "user1") group)))))))

  (testing "Creation without required fields is allowed"
    (let [group (dissoc (u/make-group {:name "name2"}) :legacy-guid)
          token (e/login (u/conn-context) "user1")
          {:keys [status concept-id revision-id]} (u/create-group token group)]
      (is (= 200 status))
      (is concept-id)
      (is (= 1 revision-id)))))





