(ns org.sysfoundry.dc.namespace
  (:require
   [schema.core :as s]
   [org.sysfoundry.gen.comms :as c])
  )


(def namespace-schema
  {
   ::name s/Str
   (s/optional-key ::doc) s/Str
   ::active s/Bool
   }
  )

(def namespaces-schema
  [namespace-schema]
  )

;;NamespaceRepo protocol definition
;;
(defprotocol NamespaceRepo
  "A protocol which provides the abstraction of a Namespace Repository"
  (list-namespaces [this account query order-by page-info] "Lists the all the namespaces in this repository matching the given query, order-by and page-info. 
                                                            this, Account and Query are mandatory parameters")
  (create-namespaces [this account namespace-defs] "Creates the namespaces for the given account. 
                                                    This method is transacational and will either create or return the errors if any without creating the others.")
  (update-namespaces [this account update-map query] "Updates the namespaces matching the given query with the values in the update-map")
  (delete-namespaces [this account query] "Deletes the namespaces matching the query")
  )