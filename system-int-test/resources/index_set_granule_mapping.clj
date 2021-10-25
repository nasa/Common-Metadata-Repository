{:start-coordinate-1-doc-values {:type "double", :doc_values true}
 :cloud-cover {:type "float"}
 :crid-id-lowercase {:type "keyword"}
 :end-coordinate-1 {:type "double"}
 :mbr-crosses-antimeridian {:type "boolean"}
 :granule-ur {:type "keyword"}
 :revision-id {:type "integer"}
 :orbit-end-clat-doc-values {:type "double", :doc_values true}
 :end-direction {:type "keyword"}
 :sensor-sn-lowercase {:type "keyword"}
 :cycle {:type "integer", :doc_values true}
 :instrument-sn-lowercase-doc-values {:type "keyword"
                                      :doc_values true}
 :lr-south {:type "float"}
 :mbr-south-doc-values {:type "float", :doc_values true}
 :start-date-doc-values {:type "date", :doc_values true}
 :native-id-lowercase {:type "keyword", :doc_values true}
 :concept-seq-id {:type "integer"}
 :concept-seq-id-long {:type "unsigned_long"}
 :orbit-start-clat-doc-values {:type "double", :doc_values true}
 :ords-info {:type "integer", :index false :store true}
 :ords {:type "integer", :index false :store true}
 :provider-id {:type "keyword"}
 :readable-granule-name-sort {:type "keyword", :doc_values true}
 :mbr-north-doc-values {:type "float", :doc_values true}
 :provider-id-lowercase-doc-values {:type "keyword", :doc_values true}
 :platform-sn-lowercase {:type "keyword"}
 :end-date {:type "date"}
 :end-coordinate-2 {:type "double"}
 :size-doc-values {:type "double", :doc_values true}
 :collection-concept-id-doc-values {:type "keyword"
                                    :doc_values true}
 :lr-north-doc-values {:type "float", :doc_values true}
 :temporals {:properties {:start-date {:type "date"}
                          :end-date {:type "date"}}
             :type "nested"
             :dynamic "strict"}
 :short-name-lowercase {:type "keyword"}
 :collection-concept-id {:type "keyword"}
 :version-id-lowercase-doc-values {:type "keyword", :doc_values true}
 :instrument-sn {:type "keyword"}
 :entry-title-lowercase-doc-values {:type "keyword", :doc_values true}
 :sensor-sn-lowercase-doc-values {:type "keyword", :doc_values true}
 :update-time {:type "keyword", :index false}
 :collection-concept-seq-id-doc-values {:type "integer"
                                        :doc_values true}
 :collection-concept-seq-id-long-doc-values {:type "unsigned_long"
                                             :doc_values true}
 :project-refs-lowercase {:type "keyword"}
 :downloadable {:type "boolean"}
 :native-id-stored {:type "keyword", :doc_values true}
 :atom-links {:type "keyword", :index false}
 :two-d-coord-name-lowercase {:type "keyword"}
 :start-coordinate-1 {:type "double"}
 :production-date {:type "date", :doc_values true}
 :entry-title-lowercase {:type "keyword"}
 :producer-gran-id-lowercase {:type "keyword", :doc_values true}
 :orbit-asc-crossing-lon {:type "double"}
 :mbr-south {:type "float"}
 :feature-id {:type "keyword"}
 :metadata-format {:type "keyword", :index false}
 :orbit-calculated-spatial-domains {:properties {:orbital-model-name {:type "keyword"}
                                                 :orbit-number {:type "integer"}
                                                 :start-orbit-number {:type "double"}
                                                 :stop-orbit-number {:type "double"}
                                                 :equator-crossing-longitude {:type "double"}
                                                 :equator-crossing-date-time {:type "date"}}
                                    :type "nested"
                                    :dynamic "strict"}
 :start-coordinate-2 {:type "double"}
 :platform-sn-lowercase-doc-values {:type "keyword", :doc_values true}
 :version-id-lowercase {:type "keyword"}
 :producer-gran-id {:type "keyword"}
 :mbr-north {:type "float"}
 :lr-east {:type "float"}
 :passes {:properties {:pass {:type "integer", :doc_values true}
                       :tiles {:type "keyword", :doc_values true}}
          :type "nested"
          :dynamic "strict"}
 :lr-west {:type "float"}
 :provider-id-lowercase {:type "keyword"}
 :size {:type "double"}
 :orbit-end-clat {:type "double"}
 :granule-ur-lowercase {:type "keyword", :doc_values true}
 :project-refs {:type "keyword"}
 :mbr-east-doc-values {:type "float", :doc_values true}
 :lr-east-doc-values {:type "float", :doc_values true}
 :orbit-calculated-spatial-domains-json {:type "keyword"
                                         :index false}
 :end-date-doc-values {:type "date", :doc_values true}
 :collection-concept-seq-id {:type "integer"}
 :collection-concept-seq-id-long {:type "unsigned_long"}
 :start-lat {:type "double"}
 :sensor-sn {:type "keyword"}
 :provider-id-doc-values {:type "keyword"
                          :doc_values true}
 :mbr-east {:type "float"}
 :lr-crosses-antimeridian {:type "boolean"}
 :end-coordinate-2-doc-values {:type "double", :doc_values true}
 :end-coordinate-1-doc-values {:type "double", :doc_values true}
 :platform-sn {:type "keyword"}
 :concept-seq-id-doc-values {:type "integer", :doc_values true}
 :concept-seq-id-long-doc-values {:type "unsigned_long", :doc_values true}
 :short-name-lowercase-doc-values {:type "keyword", :doc_values true}
 :orbit-start-clat {:type "double"}
 :native-id {:type "keyword", :doc_values true}
 :access-value-doc-values {:type "float"
                           :doc_values true}
 :revision-date-stored-doc-values {:type "date"
                                   :doc_values true}
 :browsable {:type "boolean"}
 :day-night {:type "keyword"}
 :day-night-lowercase {:type "keyword"}
 :orbit-asc-crossing-lon-doc-values {:type "double"
                                     :doc_values true}
 :coordinate-system {:type "keyword", :index false}
 :mbr-west-doc-values {:type "float", :doc_values true}
 :project-refs-lowercase-doc-values {:type "keyword"
                                     :doc_values true}
 :feature-id-lowercase {:type "keyword"}
 :mbr-west {:type "float"}
 :start-coordinate-2-doc-values {:type "double", :doc_values true}
 :start-date {:type "date"}
 :concept-id {:type "keyword"}
 :attributes {:properties {:time-value {:type "date"}
                           :float-value {:type "double"}
                           :datetime-value {:type "date"}
                           :group {:type "keyword"}
                           :int-value {:type "integer"}
                           :name {:type "keyword"}
                           :date-value {:type "date"}
                           :string-value {:type "keyword"}
                           :group-lowercase {:type "keyword"}
                           :string-value-lowercase {:type "keyword"}}
              :type "nested"
              :dynamic "strict"}
 :instrument-sn-lowercase {:type "keyword"}
 :created-at {:type "date", :doc_values true}
 :lr-north {:type "float"}
 :entry-title {:type "keyword", :index false}
 :day-night-doc-values {:type "keyword"
                        :doc_values true}
 :revision-date {:type "date"}
 :lr-south-doc-values {:type "float", :doc_values true}
 :start-direction {:type "keyword"}
 :two-d-coord-name {:type "keyword"}
 :cloud-cover-doc-values {:type "float"
                          :doc_values true}
 :revision-date-doc-values {:type "date", :doc_values true}
 :access-value {:type "float"}
 :crid-id {:type "keyword"}
 :end-lat {:type "double"}
 :lr-west-doc-values {:type "float", :doc_values true}}
