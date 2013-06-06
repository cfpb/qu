(ns cfpb.qu.query.concepts-test
  (:require [midje.sweet :refer :all]
            [cfpb.qu.query.concepts :refer :all]))

(facts "about field-name"
       (fact "it makes a field name from a concept and field"
             (field-name "toast" "brown") => "toast.brown")

       (fact "it works with keywords"
             (field-name :toast :brown) => "toast.brown"))

(facts "about db-name"
       (fact "it makes the appropriate name for getting the concept data from the DB"
             (db-name "toast" "brown") => (str prefix "toast.brown"))
       
       (fact "it works with keywords"
             (db-name :toast :brown) => "__toast.brown"))

(facts "about regex"
       (fact "it works with db-names or field-names by default"
             (re-matches (regex) "toast.brown") => ["toast.brown" "toast" "brown"]
             (re-matches (regex) "__toast.brown") => ["__toast.brown" "toast" "brown"])

       (fact "it only works with db-names if dashes-optional is set to false"
             (re-matches (regex false) "toast.brown") => nil
             (re-matches (regex) "__toast.brown") => ["__toast.brown" "toast" "brown"]))

(facts "about split"
       (fact "it gets the concept and field from a field name"
             (split "toast.brown") => [:toast :brown])

       (fact "it gets the concept and field from a db name"
             (split "__toast.brown") => [:toast :brown])

       (fact "it returns nil when it does not match"
             (split "two-three") => nil))

