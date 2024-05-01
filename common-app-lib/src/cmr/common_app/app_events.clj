(ns cmr.common-app.app-events
  "Common events which applications can use when starting and stopping."
  (:require
   [clojure.java.io :as io]
   [cmr.common-app.config :as config]
   [cmr.common.log :refer [debugf infof warnf errorf]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'com.sun.management.HotSpotDiagnosticMXBean)
(import 'java.lang.management.ManagementFactory)

;;
;; The internet suggests the following as how one would write the dump file in java:
;;
;;public static void dumpHeap (String filePath, boolean live) throws IOException {
;;  MBeanServer server = ManagementFactory.getPlatformMBeanServer ();
;;  HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy (server,
;;      "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
;;   mxBean.dumpHeap (filePath, live);
;;}

(defn- dump-heap
  "Write a diagnostics file to the file system for analysis in Visual VM"
  [^String app-name ^String file-path ^java.lang.Boolean live]
  (let [bean (-> (ManagementFactory/getPlatformMBeanServer)
                 (ManagementFactory/newPlatformMXBeanProxy "com.sun.management:type=HotSpotDiagnostic"
                                                           HotSpotDiagnosticMXBean))]
    (try
      (when (.exists (io/file file-path))
        (warnf (format "dump-heap: %s deleting diagnostic file %s." app-name file-path))
        (io/delete-file file-path))
      (.dumpHeap bean file-path live)
      (infof "dump-heap: %s finished creating diagnostic file %s." app-name file-path)
      (catch Exception err
        (errorf "dump-heap: %s because %s" app-name (.getMessage err))))))

(defn- writeDump
  "A call back function which writes a diagnostics file if the application config
   dump-diagnostics-on-exist-to has been set to a file path. To test this out,
   run the following commands:
   >CMR_DUMP_DIAGNOSTICS_ON_EXIT_TO='/tmp/dump-test.hprof'
       java -cp cmr-search-app-0.1.0-SNAPSHOT-standalone.jar clojure.main
       -m cmr.search.runner
   >kill -15 $(jps | grep main | cut -f 1 -d ' ')"
  [app-name]
  (let [path-to-file (config/dump-diagnostics-on-exit-to)]
    (infof "dump-heap: %s is writing a diagnostic file to %s..." app-name path-to-file)
    (dump-heap app-name path-to-file false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn stop-on-exit-hook
  "Add a shutdown hook to call the CMR app stop command found in most if not all
   CMR microservices. Caller must supply a function that calls the application's
   stop function such as: #(cmr.search.system/stop system)"
  [_app-name stop-thread]
  (.addShutdownHook (Runtime/getRuntime) (new Thread stop-thread)))

(defn dump-on-exit-hook
  "Add a shutdown hook to the current run time which will write a memory dump file
   if the app has been configured to do so. Set the config dump-diagnostics-on-exist-to
   a path to the file the log should be saved to."
  [app-name]
  (let [path-to-file (config/dump-diagnostics-on-exit-to)]
    (when-not (empty? path-to-file)
      (debugf "dump-heap: %s Will write a diagnostic file to %s on exit." app-name path-to-file)
      (.addShutdownHook (Runtime/getRuntime) (new Thread #(writeDump app-name))))))

(comment
  ;; use these to create a memory file locally for use in Visual VM
  (config/set-dump-diagnostics-on-exit-to! "/tmp/dump-auto.txt")
  (dump-heap "appname1" "/tmp/dump.txt" true)
  (dump-heap "appname2" "/tmp/dump.hprof" false))
