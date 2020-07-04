(ns org.sysfoundry.dc.svc
  (:require [integrant.core :as ig]
            [taoensso.timbre :as logger]
            [org.sysfoundry.system :as system]
            [org.sysfoundry.http.util :refer :all]
            [org.sysfoundry.dc.namespace :as nspace]
            )
  )


(defn list-namespaces [request]
  {:status 200 :body "List all namespaces in the system"}
  )

(defn create-namespaces [request]
  {:status 200 :body "Create new namespaces in the system"})


(defn -main [& args]
  (logger/info "Starting Decision center service...")
  (system/start {:profile :prod})
)
