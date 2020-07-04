(ns org.sysfoundry.system
  "Components and their dependency relationships"
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [integrant.core :as ig]))

(def system nil)

;; There will be integrant tags in our Aero configuration. We need to
;; let Aero know about them using this defmethod.
(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(let [lock (Object.)]
  (defn- load-namespaces
    [system-config]
    (locking lock
      (ig/load-namespaces system-config))))

(defn config
  "Read EDN config, with the given aero options. See Aero docs at
  https://github.com/juxt/aero for details."
  [opts]
  (-> (io/resource "config.edn") ;; <1>
      (aero/read-config opts)) ;; <2>
  )

(defn system-config
  "Construct a new system, configured with the given profile"
  [opts]
  (let [config (config opts) ;; <1>
        system-config (:org.sysfoundry/system config)] ;; <2>
    (load-namespaces system-config) ;; <3>
    (ig/prep system-config) ;; <4>
    ))

;;this is a penultimate service which allows a ultimate service like
;;http listener etc to depend on from initiation perspective
(defmethod ig/init-key ::penultimate-service [_ config]
  ::penultimate-service-started
  )

(defmethod ig/halt-key! ::penultimate-service [_ started-key]
  ::penultimate-service-stopped)


(defn start
  [opts]
  (let [system-config (system-config opts)
        sys (ig/init system-config)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread.
      (fn []
        (ig/halt! sys))))
    (alter-var-root #'system (constantly sys)))
  @(promise))