(ns cfpb.qu.hal-test
    (:require [midje.sweet :refer :all]
              [cfpb.qu.hal :refer :all]
              [cheshire.core :as json]))

(def resource (new-resource "http://example.org"))

(fact "new-resource sets defaults"
      (:links resource) => []
      (:embedded resource) => []
      (:properties resource) => {})

(fact "add-link adds a link to the resource"
      (add-link resource :href "/data" :rel "data")
      => (contains {:links [{:href "/data" :rel "data"}]})

      (-> resource
          (add-link :href "/data" :rel "data")
          (add-link :href "/admin" :rel "admin")
          :links)
      => [{:href "/data" :rel "data"}
          {:href "/admin" :rel "admin"}])

(fact "add-resource adds an embedded resource"
      (add-resource resource "dataset" {:href "/data/1"})
      => (contains {:embedded [["dataset" {:href "/data/1"}]]})

      (-> resource
          (add-resource "dataset" {:foo "bar"})
          (add-resource "conjurer" {:bar "baz"})
          :embedded)
      => [["dataset" {:foo "bar"}]
          ["conjurer" {:bar "baz"}]])

(fact "add-property adds one or more properties"
      (add-property resource :size 200)
      => (contains {:properties {:size 200}})

      (add-property resource :size 200 :height 12)
      => (contains {:properties {:size 200 :height 12}})

      (-> resource
          (add-property :size 200)
          (add-property :height 12 :name "Julius")
          :properties)
      => {:size 200 :height 12 :name "Julius"})

(facts "about Resource->representation"
       (fact "it transforms a simple resource into JSON"
             (json/parse-string (Resource->representation resource :json))
             => {"_links" [{"href" "http://example.org"
                            "rel" "self"}]})

       (fact "it transforms a resource with multiple links into JSON"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next"))]
               (json/parse-string (Resource->representation resource :json))
               => {"_links" [{"href" "http://example.org"
                              "rel" "self"}
                             {"href" "/admin"
                              "rel" "admin"}
                             {"href" "/?page=2"
                              "rel" "next"}]}))

       (fact "it transforms a resource with properties into JSON"
             (let [resource (-> resource
                                (add-property :size 200 :height 12))]
               (json/parse-string (Resource->representation resource :json))
               => {"_links" [{"href" "http://example.org"
                              "rel" "self"}]
                   "size" 200
                   "height" 12}))

       (fact "it transforms a resource with embedded resources into JSON"
             (let [resource (-> resource
                                (add-resource "dataset" (new-resource "/data/1"))
                                (add-resource "dataset" (new-resource "/data/2")))]
               (json/parse-string (Resource->representation resource :json))
               => {"_links" [{"href" "http://example.org"
                              "rel" "self"}]
                   "_embedded" {"datasets"
                                [{"_links" [{"href" "/data/1" "rel" "self"}]}
                                 {"_links" [{"href" "/data/2" "rel" "self"}]}]}}))

       (fact "it transforms a devilishly complex resource into JSON"
             (let [resource (-> resource
                                (add-link :href "/admin" :rel "admin")
                                (add-link :href "/?page=2" :rel "next")
                                (add-resource "dataset" (-> (new-resource "/data/1")
                                                            (add-property :name "Pete")))
                                (add-resource "dataset" (-> (new-resource "/data/2")
                                                            (add-link :href "/data/3" :rel "next")))
                                (add-property :size 200 :height 12))]
               (json/parse-string (Resource->representation resource :json))
               => {"_links" [{"href" "http://example.org"
                              "rel" "self"}
                             {"href" "/admin"
                              "rel" "admin"}
                             {"href" "/?page=2"
                              "rel" "next"}]
                   "_embedded" {"datasets"
                                [{"_links" [{"href" "/data/1" "rel" "self"}]
                                  "name" "Pete"}
                                 {"_links" [{"href" "/data/2" "rel" "self"}
                                            {"href" "/data/3" "rel" "next"}]}]}
                   "size" 200
                   "height" 12})))
