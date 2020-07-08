(ns org.sysfoundry.dc.svc
  (:require [integrant.core :as ig]
            [taoensso.timbre :as logger]
            [org.sysfoundry.system :as system]
            [org.sysfoundry.http.util :refer :all]
            [org.sysfoundry.dc.namespace :as nspace]
            [ring.util.response :as r]
            [org.sysfoundry.gen.comms :as comms]
            [clojure.edn :as edn]
            )
  )

(defn get-page-info [page size]
  #:paging{:page-num (if (nil? page) 0 page)
           :page-size (if (nil? size) 10 size)
           }
  )


(defn list-namespaces [request]
  (let [account (get-in request [:parameters :header :account])
        ;query (edn/read-string (get-in request [:parameters :query :query] []))
        query (get-in request [:parameters :query :query] [])
        ;orderby (edn/read-string (get-in request [:parameters :query :sort] nil))
        orderby (get-in request [:parameters :query :sort] nil)
        page (get-in request [:parameters :query :page] nil)
        size (get-in request [:parameters :query :size] nil)
        ns-repo (::namespace-repo request)
        ]
    (logger/debug "account : " account ", query : " query ", orderby : " orderby ", page : " page ", size : " size ", ns-repo : " ns-repo)
    (let [page-info (get-page-info page size)
          results (nspace/list-namespaces ns-repo account query orderby page-info)]
      (logger/debug "Results " results)
      (comms/on-status 
       results
       #(r/response %)
       #(r/bad-request %)
       )
      )
    )
  )

(defn create-namespaces [request]
  (logger/debug "Request -> " request)
  {:status 200 :body (:body-params request)})

(defn update-namespaces [request]
  {:status 200 :body "Update Namespaces in the system"})

(defn delete-namespaces [request]
  {:status 200 :body "Delete namespaces in the system"})

(defn get-namespace-detail [request]
  {:status 200 :body "Get details of a given namespace in the system"}
  )

(defn update-namespace-detail [request]
  {:status 200 :body "Update details of a given namespace in the system"})

(defn delete-namespace-detail [request]
  {:status 200 :body "Deletes details of a given namespace in the system"})


(defmethod ig/init-key ::middleware [_ config]
  (let [namespace-repo (:namespace-repo config)]
    (if (nil? namespace-repo)
      (throw (ex-info (str :namespace-repo " is mandatory!") {}))
      [{:name ::repo-provider
        :description "A middleware which injects the necessary namespace repository to the request"
        :wrap (fn [handler]
                (fn [req]
                  (handler (assoc req ::namespace-repo namespace-repo))))}]
      )
    )
  )

(defn -main [& args]
  (logger/info "Starting Decision center service...")
  (system/start {:profile :prod})
)
