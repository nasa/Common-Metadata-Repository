(ns cmr.umm-spec.test.xml-to-umm-mappings.dif10
  (:require [clj-time.core :as t]
            [clojure.test :refer :all]
            [cmr.umm-spec.xml-to-umm-mappings.dif10 :as parse]))

(deftest dif10-metadata-dates-test

  (testing "date elements with non-date values are skipped"
    (is (= [{:Type "UPDATE" :Date (t/date-time 2015 1 1)}]
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>obsequious lettuces</Data_Creation>
                                        <Data_Last_Revision>2015-01-01</Data_Last_Revision>
                                      </Metadata_Dates>
                                    </DIF>"))))

  (testing "valid dates return DataDates records"
    (is (= [{:Type "CREATE",
             :Date (t/date-time 2014 5 1 2 30 24)}]
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>2014-05-01T02:30:24</Data_Creation>
                                      </Metadata_Dates>
                                    </DIF>"))))

  (testing "default date is skipped"
    (is (= []
           (parse/parse-data-dates "<DIF>
                                      <Metadata_Dates>
                                        <Data_Creation>1970-01-01T00:00:00</Data_Creation>
                                      </Metadata_Dates>
                                    </DIF>")))))

(deftest dif10-temporal-end-dates
  (is (= (t/date-time 2015 1 1 23 59 59 999)
         (-> (parse/parse-dif10-xml "<DIF>
                                       <Temporal_Coverage>
                                         <Range_DateTime>
                                           <Ending_Date_Time>2015-01-01</Ending_Date_Time>
                                         </Range_DateTime>
                                       </Temporal_Coverage>
                                     </DIF>"
                                     true)
             :TemporalExtents
             first
             :RangeDateTimes
             first
             :EndingDateTime)))
  (is (= (t/date-time 2015 1 1 4 30 12)
         (-> (parse/parse-dif10-xml "<DIF>
                                       <Temporal_Coverage>
                                         <Range_DateTime>
                                           <Ending_Date_Time>2015-01-01T04:30:12</Ending_Date_Time>
                                         </Range_DateTime>
                                       </Temporal_Coverage>
                                     </DIF>"
                                     true)
             :TemporalExtents
             first
             :RangeDateTimes
             first
             :EndingDateTime))))
