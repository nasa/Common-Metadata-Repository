(ns cmr.search.test.services.aql.converters.temporal
  (:require [clojure.test :refer :all]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.date-time-parser :as dt-parser]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.services.aql.converters.temporal]
            [cmr.search.models.query :as q]))


(defn- aql-temporal-elem->condition
  [aql-snippet]
  (let [aql (format "<temporal>%s</temporal>" aql-snippet)
        xml-struct (x/parse-str aql)]
    (a/element->condition :collection xml-struct)))

(deftest aql-temporal-conversion-test
  (testing "temporal aql"
    (are
      [start-date stop-date start-day end-day aql-snippet]
      (= (q/map->TemporalCondition {:start-date (when start-date (dt-parser/parse-datetime start-date))
                                    :end-date (when stop-date (dt-parser/parse-datetime stop-date))
                                    :start-day start-day
                                    :end-day end-day})
         (aql-temporal-elem->condition aql-snippet))

      "2001-12-03T01:02:03Z" nil nil nil
      "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>"

      "2001-12-03T01:02:03Z" "2011-02-12T01:02:03Z" nil nil
      "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>
      <stopDate> <Date YYYY=\"2011\" MM=\"02\" DD=\"12\" HH=\"01\" MI=\"02\" SS=\"03\"/> </stopDate>"

      "2001-12-03T01:02:03Z" nil 32 nil
      "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>
      <startDay value=\"32\"/>"

      "2001-12-03T01:02:03Z" "2011-02-12T01:02:03Z" 32 90
      "<startDate> <Date YYYY=\"2001\" MM=\"12\" DD=\"03\" HH=\"01\" MI=\"02\" SS=\"03\"/> </startDate>
      <stopDate> <Date YYYY=\"2011\" MM=\"02\" DD=\"12\" HH=\"01\" MI=\"02\" SS=\"03\"/> </stopDate>
      <startDay value=\"32\"/> <endDay value=\"90\"/>")))
