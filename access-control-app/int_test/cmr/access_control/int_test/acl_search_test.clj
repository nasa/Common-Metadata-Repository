(ns cmr.access-control.int-test.acl-search-test
  (require [clojure.test :refer :all]
           [cmr.transmit.access-control :as ac]
           [cmr.mock-echo.client.echo-util :as e]
           [cmr.common.util :as util :refer [are2]]
           [cmr.access-control.int-test.fixtures :as fixtures]
           [cmr.access-control.test.util :as u]
           [cmr.access-control.data.access-control-index :as access-control-index]))

(use-fixtures :each
              (fixtures/reset-fixture {"prov1guid" "PROV1", "prov2guid" "PROV2"}
                                      ["user1" "user2" "user3" "user4" "user5"])
              (fixtures/grant-all-group-fixture ["prov1guid" "prov2guid"]))
(use-fixtures :once (fixtures/int-test-fixtures))


(deftest invalid-search-test
  (let [token (e/login (u/conn-context) "user1")]
    (is (= {:status 400
            :errors ["The mime types specified in the accept header [application/text] are not supported."]}
           (u/search token {} {:http-options {:accept "application/text"}})))
    (is (= {:status 400 :errors ["Parameter [foo] was not recognized."]}
           (u/search token {:foo "bar"})))))
    ;; These will come in handy once we start supporting parameters with options
    ; (is (= {:status 400 :errors ["Parameter [options[provider]] must include a nested key, options[provider][...]=value."]}
    ;        (u/search token {:provider "foo"
    ;                         "options[provider]" "foo"})))
    ; (is (= {:status 400 :errors ["Parameter [options] must include a nested key, options[...]=value."]}
    ;        (u/search token {"options" "bar"})))
    ; (is (= {:status 400 :errors ["Option [foo] is not supported for param [provider]"]}
    ;        (u/search token {:provider "PROV1"
    ;                         "options[provider][foo]" "bar"})))

(def sample-system-acl
  "TODO"
  {:group_permissions {:user_type "guest"
                       :permissions ["create" "delete"]}
   :system_identity {:target "REPLACME"}})

(defn system-acl
  "TODO"
  [target]
  (assoc-in sample-system-acl [:system_identity :target] target))

(defn ingest-acl
  "TODO"
  [token acl]
  (let [{:keys [concept_id revision_id]} (ac/create-acl (u/conn-context) acl {:token token})]
    (assoc acl :concept-id concept_id :revision-id revision_id)))

(defn acl->search-response-item
  "TODO"
  [acl]
  (let [acl (util/map-keys->kebab-case acl)]
    {:name (access-control-index/acl->display-name acl)
     :revision_id (:revision-id acl),
     :concept_id (:concept-id acl)
     :identity_type (access-control-index/acl->identity-type acl)}))

(defn acls->search-response
  "TODO"
  [hits acls]
  {:hits hits
   :items (->> acls
              (map acl->search-response-item)
              (sort-by :concept-id) ;; Sort by concept id whereever names match
              (sort-by :name)
              vec)})

(comment
 (ac/search-for-acls (u/conn-context) {})
 (ac/search-for-groups (u/conn-context) {} {:raw? true}))


;; TODO index one of each kind of ACL so that we can check the display name is correct.
;; TODO the order is by name.

;; TODO add duplicate names to make sure that it sorts by concept id

(deftest acl-search-test
  (let [token (e/login (u/conn-context) "user1")
        acl1 (ingest-acl token (system-acl "SYSTEM_AUDIT_REPORT"))
        acl2 (ingest-acl token (system-acl "METRIC_DATA_POINT_SAMPLE"))
        acl3 (ingest-acl token (system-acl "SYSTEM_INITIALIZER"))
        acl4 (ingest-acl token (system-acl "ARCHIVE_RECORD"))
        all-acls [acl1 acl2 acl3 acl4]]
    (u/wait-until-indexed)

    (is (= (acls->search-response 4 all-acls)
           (dissoc (ac/search-for-acls (u/conn-context) {}) :took)))))

    ; (testing "Search by member"
    ;   (are2 [expected-groups params]
    ;     (is (= {:status 200 :items (sort-groups expected-groups) :hits (count expected-groups)}
    ;            (select-keys (u/search token params) [:status :items :hits :errors])))
    ;
    ;     "Normal case is case insensitive"
    ;     [cmr-group1 cmr-group2 prov1-group1 prov1-group2] {:member "UsEr1"}
    ;
    ;     "Pattern"
    ;     [cmr-group1 cmr-group2 prov1-group1 prov1-group2 prov2-group1 prov2-group2]
    ;     {:member "user*" "options[member][pattern]" true}
    ;
    ;     "Multiple members"
    ;     [cmr-group2 prov1-group2 prov2-group1 prov2-group2] {:member ["user3" "user2"]}
    ;
    ;     "Multiple members - AND false (default)"
    ;     [cmr-group2 prov1-group2 prov2-group1 prov2-group2] {:member ["user3" "user2"]
    ;                                                          "options[member][and]" false}
    ;
    ;     "Multiple members - AND'd"
    ;     [prov2-group2] {:member ["user3" "user2"] "options[member][and]" true}))))


