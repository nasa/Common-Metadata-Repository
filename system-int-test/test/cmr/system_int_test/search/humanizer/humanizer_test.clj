(ns cmr.system-int-test.search.humanizer.humanizer-test
  "This tests the CMR Search API's humanizers capabilities"
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.humanizer-util :as hu]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.access-control.test.util :as u]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def field-maxes
  "A map of fields to their max lengths"
  {:type 255
   :field 255})

(defn string-of-length
  "Creates a string of the specified length"
  [n]
  (str/join (repeat n "x")))

(deftest update-humanizers-no-permission-test
  (testing "Create without token"
    (is (= {:status 401
            :errors ["You do not have permission to perform that action."]}
           (hu/update-humanizers nil (hu/make-humanizers)))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token ABC does not exist"]}
           (hu/update-humanizers "ABC" (hu/make-humanizers)))))

  (testing "Create without permission"
    (let [token (e/login (s/context) "user2")]
      (is (= {:status 401
              :errors ["You do not have permission to perform that action."]}
             (hu/update-humanizers token (hu/make-humanizers)))))))

(deftest update-humanizers-validation-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)

  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])
        valid-humanizers (hu/make-humanizers)
        valid-humanizer-rule (first (:humanizers valid-humanizers))]
    (testing "Create humanizer with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (hu/update-humanizers admin-update-token valid-humanizers {:http-options {:content-type :xml}}))))

    (testing "Create humanizer with nil body"
      (is (= {:status 400,
              :errors
              ["instance type (null) does not match any allowed primitive type (allowed: [\"object\"])"]}
             (hu/update-humanizers admin-update-token nil))))

    (testing "Create humanizer with empty array"
      (is (= {:status 400,
              :errors
              ["/humanizers array is too short: must have at least 1 elements but instance has 0 elements"]}
             (hu/update-humanizers admin-update-token {:humanizers []}))))

    (testing "Missing field validations"
      (are [field]
        (= {:status 400
            :errors [(format "/humanizers/0 object has missing required properties ([\"%s\"])"
                             (name field))]}
           (hu/update-humanizers admin-update-token {:humanizers [(dissoc valid-humanizer-rule field)]}))

        :type :field))

    (testing "Minimum field length validations"
      (are [field]
        (= {:status 400
            :errors [(format "/humanizers/0/%s string \"\" is too short (length: 0, required minimum: 1)"
                             (name field))]}
           (hu/update-humanizers admin-update-token {:humanizers [(assoc valid-humanizer-rule field "")]}))

        :type :field))

    (testing "Maximum field length validations"
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format
                            "/humanizers/0/%s string \"%s\" is too long (length: %d, maximum allowed: %d)"
                            (name field) long-value (inc max-length) max-length)]}
                 (hu/update-humanizers
                  admin-update-token {:humanizers [(assoc valid-humanizer-rule field long-value)]}))))))))

(deftest update-humanizers-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)
  (testing "Successful creation"
    (let [token (e/login (s/context) "admin" ["admin-update-group-guid"])
          humanizers (hu/make-humanizers)
          {:keys [status concept-id revision-id]} (hu/update-humanizers token humanizers)]
      (is (= 201 status))
      (is concept-id)
      (is (= 1 revision-id))
      (hu/assert-humanizers-saved humanizers "admin" concept-id revision-id)

      (testing "Successful update"
        (let [existing-concept-id concept-id
              updated-humanizers {:humanizers [(second (:humanizers humanizers))]}
              {:keys [status concept-id revision-id]} (hu/update-humanizers token updated-humanizers)]
          (is (= 200 status))
          (is (= existing-concept-id concept-id))
          (is (= 2 revision-id))
          (hu/assert-humanizers-saved updated-humanizers "admin" concept-id revision-id)))))

  (testing "Create humanizer with fields at maximum length"
    (let [token (e/login (s/context) "admin" ["admin-update-group-guid"])
          humanizers {:humanizers [(into {} (for [[field max-length] field-maxes]
                                              [field (string-of-length max-length)]))]}]
      (is (= 200 (:status (hu/update-humanizers token humanizers)))))))


(deftest get-humanizers-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)
  (testing "Get humanizer"
    (let [humanizers (hu/make-humanizers)
          token (e/login (s/context) "admin" ["admin-update-group-guid"])
          _ (hu/update-humanizers token humanizers)
          expected-humanizers {:status 200
                               :body humanizers}]

      (is (= expected-humanizers (hu/get-humanizers))))))
