(ns cmr.es-spatial-plugin.test.search-test
  "Tests the elastic spatial plugin by indexing some spatial areas in an in-memory elastic and then
  searching with a spatial area."
  (:require [clojure.test :refer :all]
            [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
            [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as esd]
            [taoensso.timbre :as timbre]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.serialize :as srl]
            [clojure.string :as str]))

(def elastic-port 9215)

(defn run-server-fixture
  [f]
  (let [server (lifecycle/start (elastic-server/create-server elastic-port (+ 10 elastic-port) "es_data/es_plugin_test") nil)]
    ;; Disables standard out logging during testing because it breaks the JUnit parser in bamboo.
    (timbre/set-config! [:appenders :standard-out :enabled?] false)
    (try
      (f)
      (finally
        (lifecycle/stop server nil)
        (timbre/set-config! [:appenders :standard-out :enabled?] true)))))

(use-fixtures :once run-server-fixture)

(defn connect
  "Connects to elastic and returns the connection"
  []
  (esr/connect (str "http://localhost:" elastic-port)))

(def index-name "spatial_areas")

(def type-name "spatial_area")

(def index-settings
  {:index
   {:number_of_shards 2,
    :number_of_replicas 1,
    :refresh_interval "1s"}})

(def mappings
  {type-name {:dynamic "strict",
              :_source {:enabled false},
              :_all {:enabled false},
              :properties {;; stores the actual ordinates (lon, lat)
                           :ords {:type "integer" :store "yes"}
                           ;; contains information about where each spatial type starts and stops
                           :ords-info {:type "integer" :store "yes"}}}})

(defn recreate-index
  "Creates the index deleting it first if it already exists."
  [conn]
  (when (esi/exists? conn index-name)
    (esi/delete conn index-name))
  (esi/create conn index-name :settings index-settings :mappings mappings))

(defn make-poly
  [coord-sys & ords]
  (poly/polygon coord-sys [(apply rr/ords->ring coord-sys ords)]))

(defn index-spatial
  "Indexes the shape and then returns it."
  [conn shape-name shape]
  (let [elastic-doc (srl/shapes->ords-info-map [shape])
        result (esd/put conn index-name type-name (name shape-name) elastic-doc)]
    (is (:created result)))
  (esi/flush conn)
  shape)

(defn search-spatial
  [conn shape]
  (let [{:keys [ords-info ords]} (srl/shapes->ords-info-map [shape])
        elastic-filter {:script {:script "spatial"
                                 :params {:ords (str/join "," ords)
                                          :ords-info (str/join "," ords-info)}
                                 :lang "native"}}
        result (esd/search conn index-name [type-name] :filter elastic-filter)]
    (set (map (comp keyword :_id) (get-in result [:hits :hits])))))

;; Commented out this test as it causes my local elastic to become red with uninitialized
;; spatial_areas shards.
#_(deftest spatial-search-test
  (let [conn (connect)
        _ (recreate-index conn)
        idx (fn [shape-name & ords]
              (index-spatial conn shape-name (apply make-poly :geodetic ords)))
        p1 (idx :p1 10 10, 30 30, 10 30, 10 10)
        p2 (idx :p2 -10 10, -10 30, -30 30, -10 10)]

    (is (= #{:p1}
           (search-spatial conn (make-poly :geodetic 12 18, 13 18, 13 22, 12 22, 12 18))))
    (is (= #{:p2}
           (search-spatial conn (make-poly :geodetic -12 18, -12 22, -13 22, -13 18, -12 18))))))