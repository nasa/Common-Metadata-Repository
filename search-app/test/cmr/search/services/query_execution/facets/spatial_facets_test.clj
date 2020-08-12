(ns cmr.search.services.query-execution.facets.spatial-facets-test
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.facets.spatial-facets :as sf]))

(deftest spatial-facet-test
  (is (= {:histogram
          {:field :cycle
           :min_doc_count 1
           :interval 1}
          :aggs {:passes
                 {:nested {:path "passes"}
                  :aggs {:pass {:histogram {:field "passes.pass"
                                            :min_doc_count 1
                                            :interval 1}}}}}}
         (sf/spatial-facet nil))))
