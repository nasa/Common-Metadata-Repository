(ns cmr.umm-spec.umm-to-xml-mappings.dif9.spatial-extent
  "These are functions that create a data structure that
   will make it easier to perform the vertical spatial translation
   for UMM -> DIF 9 records.")

(defn create-elevation-key
  "This function looks at the passed in type and checks to see if the tye contains the values
   of Altitude with Min or Max or Depth with Min or Max. If any of these match then I can
   match the type with the DIF 9 key. Otherwise pass back nil."
  [elevation-type]
  (if (.contains elevation-type "Altitude")
    (if (.contains elevation-type "Min")
      (keyword (str "Minimum_Altitude"))
      (if (.contains elevation-type "Max")
        (keyword (str "Maximum_Altitude"))))
    (if (.contains elevation-type "Depth")
      (if (.contains elevation-type "Min")
        (keyword (str "Minimum_Depth"))
        (if (.contains elevation-type "Max")
          (keyword (str "Maximum_Depth")))))))

(defn- create-vertical-domain-maps
  "Returns a vector that contains a number of maps with vertical domain data in it.
   An example return vector:
   [{:Maximum_Depth \"-100\",
     :Minimum_Depth \"-1\",
     :Minimum_Altitude \"0\",
     :Maximum_Altitude \"100\"}
    {:Minimum_Altitude \"0\",
     :Maximum_Altitude \"100\"}]
    Passed in is a vector of UMM vertical domains key and value:
    [#cmr.umm_spec.models.umm_common_models.VerticalSpatialDomainType{:Type Minimum Altitude, :Value 0}
     #cmr.umm_spec.models.umm_common_models.VerticalSpatialDomainType{:Type Maximum Altitude, :Value 100}
     ...]
    and an empty vector to start. This function is recursive and builds the vector map."
  [vert-vec result]
  (if-let [vert (first vert-vec)]
    (if (empty? result)
      ;; If the vector is empty, create a new map and add the key value pairs.
      ;; call the function again with the next vertial domain map.
      ;; If the key is nil then just call the function with the next item.
      (if-let [key (create-elevation-key (:Type vert))]
        (->> (assoc {} key (:Value vert))
             (assoc result 0)
             (create-vertical-domain-maps (drop 1 vert-vec)))
        (create-vertical-domain-maps (drop 1 vert-vec) result))
      ;; If the vector is not empty get the last map in the vector and see if the key
      ;; is alreay taken. If it is then create a new map and add the value. Add the new map to the
      ;; result vector. Then call the function again with the next vertical domain map and the new result vector.
      ;; If the key is nil then just call the function with the next item.
      (let [tmp-map (last result)]
        (if-let [key (create-elevation-key (:Type vert))]
          (if (key tmp-map)
            (->>
             (assoc {} key (:Value vert))
             (assoc result (count result))
             (create-vertical-domain-maps (drop 1 vert-vec)))
            ;; If it is not then
            ;; add the value to the existing map, replace the new map with the old map in the vector and call the
            ;; function again with the next vertical domain map and the new result vector.
            (->> (assoc tmp-map (create-elevation-key (:Type vert)) (:Value vert))
             (assoc result (- (count result) 1))
             (create-vertical-domain-maps (drop 1 vert-vec))))
          (create-vertical-domain-maps (drop 1 vert-vec) result))))
    result))

(defn create-vertical-domain-vector-maps
  "This function creates a vector of maps that contain vertical spatial domain data.
   This data structure is used so that we can fill in the DIF 9 XML vertical spatail domain elements more easily."
  [c]
  (if-let [vert (-> c :SpatialExtent :VerticalSpatialDomains)]
    (create-vertical-domain-maps vert [])))
