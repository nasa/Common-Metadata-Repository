(ns cmr.system-int-test.virtual-product.virtual-product-token-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.virtual-product-util :as vp]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.system :as s]
            [cmr.mock-echo.client.echo-util :as e]))

(use-fixtures :each (ingest/reset-fixture {"LPDAAC_ECS_guid" "LPDAAC_ECS"} {:grant-all-ingest? false}))

(deftest ingest-with-system-token-test
  (let [prov-admin-update-group-concept-id (e/get-or-create-group (s/context) "prov-admin-update-group")
        mock-admin-group-concept-id (e/get-or-create-group (s/context) "prov-admin-update-group")
        _ (e/grant-group-provider-admin (s/context) prov-admin-update-group-concept-id "LPDAAC_ECS" :update)
        ;; Grant ingest permission on LPDAAC_ECS to the group to which mock echo system token belongs
        _ (e/grant-group-provider-admin (s/context) mock-admin-group-concept-id "LPDAAC_ECS" :update)
        provider-admin-update-token (e/login (s/context) "prov-admin-update"
                                             [prov-admin-update-group-concept-id])

        [ast-coll] (vp/ingest-source-collections
                     [(assoc
                        (dc/collection
                          {:entry-title "ASTER L1A Reconstructed Unprocessed Instrument Data V003"
                           :short-name "AST_L1A"})
                        :provider-id "LPDAAC_ECS")]
                     {:token provider-admin-update-token})
        vp-colls (vp/ingest-virtual-collections [ast-coll] {:token provider-admin-update-token})
        granule-ur "SC:AST_L1A.003:2006227720"
        ast-l1a-gran (vp/ingest-source-granule "LPDAAC_ECS"
                                    (dg/granule ast-coll {:granule-ur granule-ur})
                                    :token provider-admin-update-token)
        expected-granule-urs (vp/source-granule->virtual-granule-urs ast-l1a-gran)
        all-expected-granule-urs (cons (:granule-ur ast-l1a-gran) expected-granule-urs)]
    (index/wait-until-indexed)
    (vp/assert-matching-granule-urs
      all-expected-granule-urs
      (search/find-refs :granule {:page-size 50}))))
