(defproject nasa-cmr/cmr "0.1.0-SNAPSHOT"
  :plugins [[lein-modules "0.3.11"]]

  :profiles
  {:uberjar
   {:modules {:dirs ["ingest-app"
                     "bootstrap-app"
                     "index-set-app"
                     "mock-echo-app"
                     "es-spatial-plugin"
                     "system-int-test"
                     "dev-system"]}}})

