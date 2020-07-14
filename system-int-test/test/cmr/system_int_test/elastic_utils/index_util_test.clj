(ns cmr.system-int-test.elastic-utils.index-util-test
  (:require [clojure.test :refer :all]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [cmr.common.util :refer [are3]]
            [cmr.elastic-utils.index-util :as es-util]
            [cmr.system-int-test.utils.url-helper :as url]))

(defn es-fixture
  [f]
  (esd/create (esr/connect (url/elastic-root))
              "i_exist"
              "doc"
              {:title "mock doc"
               :created_on "today"})
  (f))

(use-fixtures :each es-fixture)

(deftest index-exists-test
  (are3
    [index-name expected]
    (let [conn (esr/connect (url/elastic-root))]
      (is (= expected (es-util/index-exists? conn index-name))))

    "returns true if the index exists"
    "i_exist" true

    "returns false if the index does not exist"
    "i_have_not_been_created" false))

