(ns cmr.system-int-test.ingest.provider-ingest-permissions-test
  "Verifies the correct provider ingest permissions are enforced"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.echo-util :as e]
            [cmr.system-int-test.utils.url-helper :as url]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"} false))

(comment
  (do
    (ingest/create-provider "provguid1" "PROV1")
    (e/grant-all-ingest "provguid1")
    (ingest/create-provider "provguid2" "PROV2")
    (e/grant-all-ingest "provguid2")
    ))

(def echo10-headers
  {:Content-type "application/echo10+xml"})

(def collection-xml
  "<Collection><ShortName>ShortName_Larc</ShortName><VersionId>Version01</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate><LongName>LarcLongName</LongName>
  <DataSetId>LarcDatasetId</DataSetId><Description>A minimal valid collection</Description>
  <Orderable>true</Orderable><Visible>true</Visible><Campaigns><Campaign>
  <ShortName>LARC_short_name</ShortName></Campaign></Campaigns></Collection>")

(def granule-xml
  "<Granule><GranuleUR>SC:AE_5DSno.002:30500511</GranuleUR>
  <InsertTime>2009-05-11T20:09:16.340Z</InsertTime>
  <LastUpdate>2014-03-19T09:59:12.207Z</LastUpdate>
  <Collection><DataSetId>LarcDatasetId</DataSetId></Collection><Orderable>true</Orderable>
  </Granule>")

(deftest ingest-provider-management-granule-permission-test
  ;; Grant provider admin permission
  (e/grant-group-provider-admin "prov-admin-read-group-guid" "provguid1" :read)
  (e/grant-group-provider-admin "prov-admin-update-group-guid" "provguid1" :update)
  (e/grant-group-provider-admin "prov-admin-read-update-group-guid" "provguid1" :read :update)
  (e/grant-group-provider-admin "prov-admin-update-delete-group-guid" "provguid1" :delete :update)
  ;; Grant provider admin permission but for a different provider
  (e/grant-group-provider-admin "another-prov-admin-group-guid" "provguid2" :read :update :delete)
  ;; Grant system admin permission - but not provider admin
  (e/grant-group-admin "ingest-super-admin-group-guid" :read :update :delete)

  (let [guest-token (e/login-guest)
        user-token (e/login "user1" ["plain-group-guid2" "plain-group-guid3"])
        provider-admin-read-token (e/login "prov-admin-read" ["prov-admin-read-group-guid"
                                                              "plain-group-guid3"])
        provider-admin-update-token (e/login "prov-admin-update" ["prov-admin-update-group-guid"
                                                                  "plain-group-guid3"])
        provider-admin-read-update-token (e/login "prov-admin-read-update"
                                                  ["prov-admin-read-update-group-guid"
                                                   "plain-group-guid3"])
        provider-admin-update-delete-token (e/login "prov-admin-update-delete"
                                                    ["prov-admin-update-delete-group-guid"
                                                     "plain-group-guid3"])
        another-prov-admin-token (e/login "another-prov-admin" ["another-prov-admin-group-guid"
                                                                "plain-group-guid3"])
        super-admin-token (e/login "super-admin" ["ingest-super-admin-group-guid"])
        collection-ingest-url (url/ingest-url "PROV1" :collection "native-id")
        granule-ingest-url (url/ingest-url "PROV1" :granule "G1-ABC")]

    ; Ingest the collection that will be used for the granule tests
    (e/has-action-permission?
      collection-ingest-url :put provider-admin-update-delete-token echo10-headers
      collection-xml)

    (testing "ingest granule update permissions"
      (is (e/has-action-permission?
            granule-ingest-url :put provider-admin-update-token echo10-headers granule-xml))
      (is (e/has-action-permission?
            granule-ingest-url :put provider-admin-update-token echo10-headers granule-xml))
      (is (e/has-action-permission?
            granule-ingest-url :put provider-admin-read-update-token echo10-headers granule-xml))
      (is (e/has-action-permission?
            granule-ingest-url :put provider-admin-update-delete-token echo10-headers granule-xml))
      (is (not (e/has-action-permission?
                 granule-ingest-url :put guest-token echo10-headers granule-xml)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :put user-token echo10-headers granule-xml)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :put super-admin-token echo10-headers granule-xml)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :put another-prov-admin-token echo10-headers granule-xml)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :put provider-admin-read-token echo10-headers granule-xml))))
    (testing "ingest granule delete permissions"
      (is (not (e/has-action-permission?
                 granule-ingest-url :delete provider-admin-update-token echo10-headers)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :delete provider-admin-update-token echo10-headers)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :delete provider-admin-read-update-token echo10-headers)))
      (is (e/has-action-permission?
            granule-ingest-url :delete provider-admin-update-delete-token echo10-headers))
      (is (not (e/has-action-permission? granule-ingest-url :delete guest-token echo10-headers)))
      (is (not (e/has-action-permission? granule-ingest-url :delete user-token echo10-headers)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :delete super-admin-token echo10-headers)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :delete another-prov-admin-token echo10-headers)))
      (is (not (e/has-action-permission?
                 granule-ingest-url :delete provider-admin-read-token echo10-headers))))))

(deftest ingest-provider-management-collection-permission-test
  ;; Grant provider admin permission
  (e/grant-group-provider-admin "prov-admin-read-group-guid" "provguid1" :read)
  (e/grant-group-provider-admin "prov-admin-update-group-guid" "provguid1" :update)
  (e/grant-group-provider-admin "prov-admin-read-update-group-guid" "provguid1" :read :update)
  (e/grant-group-provider-admin "prov-admin-update-delete-group-guid" "provguid1" :delete :update)
  ;; Grant provider admin permission but for a different provider
  (e/grant-group-provider-admin "another-prov-admin-group-guid" "provguid2" :read :update :delete)
  ;; Grant system admin permission - but not provider admin
  (e/grant-group-admin "ingest-super-admin-group-guid" :read :update :delete)

  (let [guest-token (e/login-guest)
        user-token (e/login "user1" ["plain-group-guid2" "plain-group-guid3"])
        provider-admin-read-token (e/login "prov-admin-read" ["prov-admin-read-group-guid"
                                                              "plain-group-guid3"])
        provider-admin-update-token (e/login "prov-admin-update" ["prov-admin-update-group-guid"
                                                                  "plain-group-guid3"])
        provider-admin-read-update-token (e/login "prov-admin-read-update"
                                                  ["prov-admin-read-update-group-guid"
                                                   "plain-group-guid3"])
        provider-admin-update-delete-token (e/login "prov-admin-update-delete"
                                                    ["prov-admin-update-delete-group-guid"
                                                     "plain-group-guid3"])
        another-prov-admin-token (e/login "another-prov-admin" ["another-prov-admin-group-guid"
                                                                "plain-group-guid3"])
        super-admin-token (e/login "super-admin" ["ingest-super-admin-group-guid"])
        collection-ingest-url (url/ingest-url "PROV1" :collection "native-id")]

    (testing "ingest collection update permissions"
      (is (e/has-action-permission?
            collection-ingest-url :put provider-admin-update-token echo10-headers collection-xml))
      (is (e/has-action-permission?
            collection-ingest-url :put provider-admin-update-token echo10-headers collection-xml))
      (is (e/has-action-permission?
            collection-ingest-url :put provider-admin-read-update-token echo10-headers
            collection-xml))
      (is (e/has-action-permission?
            collection-ingest-url :put provider-admin-update-delete-token echo10-headers
            collection-xml))
      (is (not (e/has-action-permission?
                 collection-ingest-url :put guest-token echo10-headers collection-xml)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :put user-token echo10-headers collection-xml)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :put super-admin-token echo10-headers collection-xml)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :put another-prov-admin-token echo10-headers
                 collection-xml)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :put provider-admin-read-token echo10-headers
                 collection-xml))))
    (testing "ingest collection delete permissions"
      (is (not (e/has-action-permission?
                 collection-ingest-url :delete provider-admin-update-token echo10-headers)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :delete provider-admin-update-token echo10-headers)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :delete provider-admin-read-update-token echo10-headers)))
      (is (e/has-action-permission?
            collection-ingest-url :delete provider-admin-update-delete-token echo10-headers))
      (is (not (e/has-action-permission? collection-ingest-url :delete guest-token echo10-headers)))
      (is (not (e/has-action-permission? collection-ingest-url :delete user-token echo10-headers)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :delete super-admin-token echo10-headers)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :delete another-prov-admin-token echo10-headers)))
      (is (not (e/has-action-permission?
                 collection-ingest-url :delete provider-admin-read-token echo10-headers))))))