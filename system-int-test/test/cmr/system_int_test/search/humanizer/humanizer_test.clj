(ns cmr.system-int-test.search.humanizer.humanizer-test
  "This tests the CMR Search API's humanizer capabilities"
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

(deftest update-humanizer-no-permission-test
  (testing "Create without token"
    (is (= {:status 401
            :errors ["You do not have permission to perform that action."]}
           (hu/update-humanizer nil (hu/make-humanizer)))))

  (testing "Create with unknown token"
    (is (= {:status 401
            :errors ["Token ABC does not exist"]}
           (hu/update-humanizer "ABC" (hu/make-humanizer)))))

  (testing "Create without permission"
    (let [token (e/login (s/context) "user2")]
      (is (= {:status 401
              :errors ["You do not have permission to perform that action."]}
             (hu/update-humanizer token (hu/make-humanizer)))))))

(deftest update-humanizer-validation-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)

  (let [admin-update-token (e/login (s/context) "admin" ["admin-update-group-guid"])
        valid-humanizer (hu/make-humanizer)
        valid-humanizer-rule (first valid-humanizer)]
    (testing "Create humanizer with invalid content type"
      (is (= {:status 400,
              :errors
              ["The mime types specified in the content-type header [application/xml] are not supported."]}
             (hu/update-humanizer admin-update-token valid-humanizer {:http-options {:content-type :xml}}))))

    (testing "Missing field validations"
      (are [field]
           (= {:status 400
               :errors [(format "/0 object has missing required properties ([\"%s\"])"
                                (name field))]}
              (hu/update-humanizer admin-update-token [(dissoc valid-humanizer-rule field)]))

           :type :field))

    (testing "Minimum field length validations"
      (are [field]
           (= {:status 400
               :errors [(format "/0/%s string \"\" is too short (length: 0, required minimum: 1)"
                                (name field))]}
              (hu/update-humanizer admin-update-token [(assoc valid-humanizer-rule field "")]))

           :type :field))

    (testing "Maximum field length validations"
      (doseq [[field max-length] field-maxes]
        (let [long-value (string-of-length (inc max-length))]
          (is (= {:status 400
                  :errors [(format
                             "/0/%s string \"%s\" is too long (length: %d, maximum allowed: %d)"
                             (name field) long-value (inc max-length) max-length)]}
                 (hu/update-humanizer
                   admin-update-token [(assoc valid-humanizer-rule field long-value)]))))))))

(deftest update-humanizer-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)
  (testing "Successful creation"
    (let [token (e/login (s/context) "admin" ["admin-update-group-guid"])
          humanizer (hu/make-humanizer)
          {:keys [status concept-id revision-id]} (hu/update-humanizer token humanizer)]
      (is (= 200 status))
      (is concept-id)
      (is (= 1 revision-id))
      (hu/assert-humanizer-saved humanizer "admin" concept-id revision-id)

      (testing "Successful update"
        (let [existing-concept-id concept-id
              updated-humanizer [(second humanizer)]
              {:keys [status concept-id revision-id]} (hu/update-humanizer token updated-humanizer)]
          (is (= 200 status))
          (is (= existing-concept-id concept-id))
          (is (= 2 revision-id))
          (hu/assert-humanizer-saved updated-humanizer "admin" concept-id revision-id)))))

  (testing "Create humanizer with fields at maximum length"
    (let [token (e/login (s/context) "admin" ["admin-update-group-guid"])
          humanizer [(into {} (for [[field max-length] field-maxes]
                                [field (string-of-length max-length)]))]]
      (is (= 200 (:status (hu/update-humanizer token humanizer)))))))


(deftest get-humanizer-test
  (e/grant-group-admin (s/context) "admin-update-group-guid" :update)
  (testing "Get humanizer"
    (let [humanizer (hu/make-humanizer)
          token (e/login (s/context) "admin" ["admin-update-group-guid"])
          _ (hu/update-humanizer token humanizer)
          expected-humanizer {:status 200
                              :body humanizer}]

      (is (= expected-humanizer (hu/get-humanizer))))))

