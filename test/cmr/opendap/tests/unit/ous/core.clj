(ns cmr.opendap.tests.unit.ous.core
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.core :as core]
    [cmr.opendap.ous.variable :as variable]))

(deftest bounding-info->opendap-query
  (testing "No bounds ..."
   (is (= "?MyVar,Latitude,Longitude"
          (core/bounding-info->opendap-query [{:name "MyVar"}])))
   (is (= "?MyVar1,MyVar2,Latitude,Longitude"
          (core/bounding-info->opendap-query
           [{:name "MyVar1"} {:name "MyVar2"}]))))
  (testing "With bounds ..."
    (let [bounds [-27.421875 53.296875 18.5625 69.75]
          dims {:Longitude 360 :Latitude 180}
          bounding-info [{:name "MyVar"
                          :bounds bounds
                          :opendap (variable/create-opendap-bounds
                                    dims bounds)}]]
     (is (= "?MyVar[20:1:37][152:1:199],Latitude[20:1:37],Longitude[152:1:199]"
            (core/bounding-info->opendap-query bounding-info bounds))))))
