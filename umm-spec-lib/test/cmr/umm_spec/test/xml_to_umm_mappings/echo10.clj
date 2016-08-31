(ns cmr.umm-spec.test.xml-to-umm-mappings.echo10
  (:require
   [clojure.test :refer :all]
   [cmr.umm-spec.xml-to-umm-mappings.echo10.data-contact :as contact]))

(deftest echo10-contact-role-test
  (testing "ECHO10 Contact with invalid role and default applied"
    ;; Test that when the contact role is invalid, the data center defaults to the correct role
    (let [xml "<Collection>
                <Contacts>
                 <Contact>
                  <Role>USER SERVICES</Role>
                  <OrganizationName>LPDAAC</OrganizationName>
                 </Contact>
                </Contacts>
               </Collection>"
          data-centers (contact/parse-data-centers xml true)]
      (is (= [contact/default-data-center-role] (:Roles (first data-centers)))))
   (testing "ECHO10 Contact with invalid role and no default applied"
     (let [xml "<Collection>
                 <Contacts>
                  <Contact>
                   <Role>USER SERVICES</Role>
                   <OrganizationName>LPDAAC</OrganizationName>
                  </Contact>
                 </Contacts>
                </Collection>"
           data-centers (contact/parse-data-centers xml false)]
       (is (= [] (:Roles (first data-centers))))))))
