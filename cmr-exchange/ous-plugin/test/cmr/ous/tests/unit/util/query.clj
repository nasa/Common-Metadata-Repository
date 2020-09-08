(ns cmr.ous.tests.unit.util.query
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [clojure.string :as string]
    [cmr.ous.util.query :as query])
  (:refer-clojure :exclude [parse]))

(deftest parse
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
                                                               :bounding-box nil
                                                               :collection-id "C130"
                                                               :exclude-granules false
                                                               :format nil
                                                               :granules ()
                                                               :subset nil
                                                               :temporal []
                                                               :variables ()}

         (query/parse {:collection-id "C130"})))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
                                                               :bounding-box nil
                                                               :collection-id "C130"
                                                               :exclude-granules false
                                                               :format nil
                                                               :granules ()
                                                               :subset []
                                                               :temporal []
                                                               :variables ()}

         (query/parse {:collection-id "C130" :subset []})))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
                                                               :collection-id "C130"
                                                               :format "nc"
                                                               :granules []
                                                               :exclude-granules false
                                                               :variables ["V234" "V345"]
                                                               :subset nil
                                                               :bounding-box nil
                                                               :temporal []})
      (query/parse {:collection-id "C130" :variables ["V234" "V345"]}))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
                                                               :collection-id "C130"
                                                               :format "nc"
                                                               :granules []
                                                               :exclude-granules false
                                                               :variables ["V234" "V345"]
                                                               :subset nil
                                                               :bounding-box nil
                                                               :temporal []})
      (query/parse {:collection-id "C130" "variables[]" ["V234" "V345"]}))
  (is (= {:errors ["The following required parameters are missing from the request: [:collection-id]"]}
         (query/parse {:variables ["V234" "V345"]})))
  (is (= {:errors ["One or more of the parameters provided were invalid."
                   "Parameters: {:collection-id \"C130\", :blurg \"some weird data\"}"]}
         (query/parse {:collection-id "C130" :blurg "some weird data"}))))
