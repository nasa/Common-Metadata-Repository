(ns cmr.system-int-test.logging.runtime-change-test
  "This tests the CMR runtime change logging capabilities"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common-app.services.logging-config :as common-logging-config]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.logging-util :as log]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture
                     {"provguid1" "PROV1"}
                     {:grant-all-search? false
                      :grant-all-ingest? false}))

;bootstrap is different and virtual-product I had problems - will address these later.
(def logging-apps-to-test
  [:search :ingest :access-control :index-set :indexer :metadata-db :cubby])

; Currently the integration tests logging level is set to debug - so set it to info for this test.
(def merge-configuration
  (str {:level :info
        :ns-pattern-map {"cmr.common-app.api.log" :debug :all :info}}))

(deftest logging-configuration-tests
  (let [admin-update-group-concept-id (e/get-or-create-group (s/context) "admin-update-group")
        admin-update-token (e/login (s/context) "admin" [admin-update-group-concept-id])
        user-token (e/login (s/context) "user1")
        _ (e/grant-group-admin (s/context) admin-update-group-concept-id :update)]
    (doseq [app logging-apps-to-test]
      (testing (str (name app) " tests: ")
        (testing "Get logging configuration without token"
          (is (= {:status 401
                  :errors ["You do not have permission to perform that action."]
                  :headers {"Content-Type" "application/edn"}}
                 (log/get-logging-configuration app))))

        (testing "Get logging configuration with unknown token"
          (is (= {:status 401
                  :errors ["Token DEF does not exist"]
                  :headers {"Content-Type" "application/edn"}}
                 (log/get-logging-configuration app "DEF"))))

        (testing "Get logging configuration without permission"
          (is (= {:status 401
                  :errors ["You do not have permission to perform that action."]
                  :headers {"Content-Type" "application/edn"}}
                 (log/get-logging-configuration app user-token))))

        (testing "Get logging configuration test as admin with non html return"
          (let [http-options {:http-options {:accept mt/json}}
                expected-config (common-logging-config/get-logging-config false)]
            (is (= expected-config (log/get-logging-configuration app admin-update-token http-options)))))

        (testing "Get logging configuration test as admin with html return"
          (let [http-options {:http-options {:accept mt/html}}
                expected-config (common-logging-config/get-logging-config true)]
            (is (= expected-config (log/get-logging-configuration app admin-update-token http-options)))))

        (testing "Merge logging configuration without token"
          (is (= {:status 401
                  :errors ["You do not have permission to perform that action."]
                  :headers {"Content-Type" "application/edn"}}
                 (log/merge-logging-configuration app merge-configuration))))

        (testing "Merge logging configuration with unknown token"
          (is (= {:status 401
                  :errors ["Token DEF does not exist"]
                  :headers {"Content-Type" "application/edn"}}
                 (log/merge-logging-configuration app merge-configuration "DEF"))))

        (testing "Merge logging configuration without permission"
          (let [token (e/login (s/context) "user2")]
            (is (= {:status 401
                    :errors ["You do not have permission to perform that action."]
                    :headers {"Content-Type" "application/edn"}}
                   (log/merge-logging-configuration app merge-configuration token)))))

        (testing "Merge logging configuration test as admin with non html return"
          (let [http-options {:http-options {:accept mt/json}}
                expected-config (common-logging-config/merge-logging-config merge-configuration false)]
            (is (= expected-config (log/merge-logging-configuration app merge-configuration admin-update-token http-options)))))

        (testing "Merge logging configuration test as admin with html return"
          (let [http-options {:http-options {:accept mt/html}}
                expected-config (common-logging-config/merge-logging-config merge-configuration true)]
            (is (= expected-config (log/merge-logging-configuration app merge-configuration admin-update-token http-options)))))

        (testing "Reset logging configuration without token"
          (is (= {:status 401
                  :errors ["You do not have permission to perform that action."]
                  :headers {"Content-Type" "application/edn"}}
                 (log/reset-logging-configuration app))))

        (testing "Reset logging configuration with unknown token"
          (is (= {:status 401
                  :errors ["Token DEF does not exist"]
                  :headers {"Content-Type" "application/edn"}}
                 (log/reset-logging-configuration app "DEF"))))

        (testing "Reset logging configuration without permission"
          (let [token (e/login (s/context) "user2")]
            (is (= {:status 401
                    :errors ["You do not have permission to perform that action."]
                    :headers {"Content-Type" "application/edn"}}
                   (log/reset-logging-configuration app token)))))

        (testing "Reset logging configuration test as admin with non html return"
          (let [http-options {:http-options {:accept mt/json}}
                expected-config (common-logging-config/reset-logging-config false)]
            (is (= expected-config (log/reset-logging-configuration app admin-update-token http-options)))))

        (testing "Reset logging configuration test as admin with html return"
          (let [http-options {:http-options {:accept mt/html}}
                expected-config (common-logging-config/reset-logging-config true)]
            (is (= expected-config (log/reset-logging-configuration app admin-update-token http-options)))))))))
