(ns cmr.opendap.dev
  "CMR OPeNDAP development namespace."
  (:require
   [cheshire.core :as json]
   [clojure.data.xml :as xml]
   [clojure.java.classpath :as classpath]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.tools.namespace.repl :as repl]
   [clojusc.system-manager.core :as system-api :refer :all]
   [clojusc.twig :as logger]
   [cmr.authz.components.caching :as auth-caching]
   [cmr.exchange.common.util :as common-util]
   [cmr.exchange.common.util :as util]
   [cmr.exchange.query.impl.cmr :as cmr]
   [cmr.exchange.query.impl.wcs :as wcs]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.components.caching :as concept-caching]
   [cmr.metadata.proxy.concepts.variable :as variable]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.components.core]
   [cmr.opendap.config :as config-lib]
   [cmr.opendap.testing.util :as testing-util]
   [cmr.ous.results.errors :as errors]
   ; [cmr.ous.util.geog :as geog]
   [cmr.plugin.jar.components.registry :as plugin-registry]
   [cmr.plugin.jar.core :as jar-plugin]
   [cmr.plugin.jar.jarfile :as jarfile]
   [com.stuartsierra.component :as component]
   [debugger.core :as debug]
   [environ.core :as environ]
   [org.httpkit.client :as httpc]
   [ring.util.codec :as codec]
   [trifl.java :refer [show-methods]]
   [xml-in.core :as xml-in])
  (:import
   (cmr.exchange.query.impl.cmr CollectionCmrStyleParams)
   (cmr.exchange.query.impl.wcs CollectionWcsStyleParams)
   (java.net URI)
   (java.nio.file Paths)
   ; (net.sf.geographiclib Geodesic PolygonArea)
   ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def setup-options {
  :init 'cmr.opendap.components.core/init
  :after-refresh 'cmr.opendap.dev/init-and-startup
  :throw-errors false})

(defn init
  []
  "This is used to set the options and any other global data.

  This is defined in a function for re-use. For instance, when a REPL is
  reloaded, the options will be lost and need to be re-applied."
  (logger/set-level! '[cmr] :debug)
  (setup-manager setup-options))

(defn init-and-startup
  []
  "This is used as the 'after-refresh' function by the REPL tools library.
  Not only do the options (and other global operations) need to be re-applied,
  the system also needs to be started up, once these options have be set up."
  (init)
  (startup))

;; It is not always desired that a system be started up upon REPL loading.
;; Thus, we set the options and perform any global operations with init,
;; and let the user determine when then want to bring up (a potentially
;; computationally intensive) system.
(init)

(defn banner
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)

