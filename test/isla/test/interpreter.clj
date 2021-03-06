(ns isla.test.interpreter
  (:use [isla.interpreter])
  (:use [isla.parser])
  (:use [isla.user])
  (:require [isla.library :as library])
  (:use [clojure.test])
  (:use [clojure.pprint])
  (:require [mrc.utils :as utils]))

(defrecord Person [age name friend items])
(def extra-types
  {"person" (fn [] (new isla.test.interpreter.Person 0 "" :undefined (isla-list)))})

(defn new-person
  ([] (new-person 0))
  ([age] (new-person age ""))
  ([age name] (new-person age name :undefined))
  ([age name friend] (new-person age name friend (isla-list)))
  ([age name friend items]
     (map->Person {:age age :name name :friend friend :items items})))

;; non-slot assignment

(deftest integer-assignment
  (let [result (interpret (parse "isla is 1"))]
    (is (= (get (:ctx result) "isla")
           1))))

(deftest test-lookup-resolves-to-referenced-data-with-updated-value
  (let [env (interpret (parse "isla is a person\nfriend is isla\nisla age is 1")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "friend"} env)
           (new-person 1)))))

(deftest test-lookup-resolves-object-slot-to-object-reference-with-updated-data
  (let [env (interpret (parse "isla is a person
                               mary is a person
                               mary friend is isla
                               isla age is 1")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "mary"} env)
           (let [isla (new-person 1)]
             (new-person 0 "" isla))))))

;; invocation

(deftest test-single-invoke-returns-return-value
  (let [result (interpret (parse "write 2"))]
    (is (= (:ret result) 2))))

(deftest test-next-expression-overwrites-ret-of-previous
  (let [result (interpret (parse "write 2\nwrite 3"))]
    (is (= (:ret result) 3))))

(deftest test-second-not-returning-expression-removes-ret-value-of-prev
  (let [result (interpret (parse "write 2"))]
    (is (= (:ret result) 2)) ;; check first would have ret val

    ;; run test
    (let [result (interpret (parse "write 2\nage is 1"))]
    (is (nil? (:ret result))))))

(deftest test-write-assigned-value
  (let [result (interpret (parse "name is 'mary'\nwrite name"))]
    (is (= (:ret result) "mary"))))

(deftest test-invoking-with-variable-param
  (let [result (interpret (parse "age is 1\nwrite age"))]
    (is (= (:ret result) 1))))

(deftest test-invoking-with-object-param
  (let [result (interpret (parse "mary is a person\nmary age is 2\nwrite mary age")
                          (library/get-initial-env extra-types))]
    (is (= (:ret result) 2))))

;; type assignment

(deftest test-type-assignment
  (let [result (interpret (parse "isla is a person")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "isla")
           (new-person)))))

(deftest test-type-assignment-of-a-generic-type
  (let [result (interpret (parse "isla is a giraffe")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "isla")
           {}))))

;; slot assignment

(deftest test-slot-assignment
  (let [result (interpret (parse "isla is a person\nisla age is 1")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "isla")
           (new-person 1)))))

(deftest test-slot-assignment-retention-of-other-slot-values
  (let [result (interpret (parse "isla is a person\nisla name is 'isla'\nisla age is 1")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "isla")
           (new-person 1 "isla")))))

(deftest test-non-existent-slot-assignment
  (let [result (interpret (parse "isla is a person\nisla material is 'metal'")
                          (library/get-initial-env extra-types))]
   (is (= (get-in result [:ctx "isla" :material])
          "metal"))))

(deftest test-slot-type-assignment
  (let [result (interpret (parse "isla is a person\nisla friend is a person")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "isla")
           (new-person 0 "" (new-person))))))

(deftest test-generic-slot-assignment
  (let [result (interpret (parse "isla is a monkey\nisla age is 1")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "isla")
           {:age 1}))))


;; assignment

(deftest test-non-canonical-assigned-obj-is-ref
  (let [result (interpret (parse "mary is a person\nfriend is mary")
                          (library/get-initial-env extra-types))]
    (is (= (get (:ctx result) "friend")
           {:ref "mary"}))))

;; test extract fn

(deftest test-extract-block-tag
  (let [ast (parse "isla is a person")]
    (is (= (utils/extract ast [:c 0 :tag]) :block))))

(deftest test-extract-way-deep-assignee-scalar-name
  (let [ast (parse "isla is a person")]
    (is (= (utils/extract ast [:c 0 :c 0 :c 0
                         :c 0 :c 0 :c 0 :c 0]) "isla"))))

(deftest test-extract-way-deep-identifier-tag
  (let [ast (parse "isla is a person")]
    (is (= (utils/extract ast [:c 0 :c 0 :c 0
                         :c 0 :c 0 :c 0 :tag]) :identifier))))

;; lists

(deftest test-instantiate-list
  (let [env (interpret (parse "items is a list")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list)))))

(deftest test-unknown-scalar-list-add-causes-exception
  (try
    (interpret (parse "add 'sword' to items")
               (library/get-initial-env extra-types))
    (is false) ;; should not get called
    (catch Exception e
      (is (= (.getMessage e) "I do not know of a list called items.")))))

(deftest test-unknown-object-attr-list-add-causes-exception
  (try
    (interpret (parse "add 1 to isla items")
               (library/get-initial-env extra-types))
    (is false) ;; should not get called
    (catch Exception e
      (is (= (.getMessage e) "I do not know of a list called isla items.")))))

;; list addition

(deftest test-add-list
  (let [env (interpret (parse "items is a list\nadd 'sword' to items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list "sword")))))

(deftest test-add-obj-to-list-works-with-ref
  (let [env (interpret (parse "items is a list\nmary is a person\nadd mary to items")
                       (library/get-initial-env extra-types))]
    (is (= (get (:ctx env) "items") (isla-list {:ref "mary"})))))

(deftest test-add-duplicate-item-string
  (let [env (interpret (parse "items is a list\nadd 'sword' to items\nadd 'sword' to items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list "sword" "sword")))))

(deftest test-add-duplicate-item-integer
  (let [env (interpret (parse "items is a list\nadd 1 to items\nadd 1 to items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list 1 1)))))

(deftest test-add-duplicate-item-does-nothing-obj
  (let [env (interpret (parse "mary is a person\nitems is a list
                               add mary to items\nadd mary to items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list ((get extra-types "person")))))))

(deftest test-add-to-obj-attribute-list
  (let [env (interpret (parse "isla is a person\nadd 1 to isla items")
                       (library/get-initial-env extra-types))]
    (is (= (-> (resolve- {:ref "isla"} env) :items)
           (isla-list 1)))))

;; list removal

(deftest test-remove-list
  (let [env (interpret (parse "items is a list\nadd 'sword' to items\nremove 'sword' from items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list)))))

(deftest test-remove-obj-from-list-works-with-ref
  (let [env (interpret (parse "items is a list\nmary is a person
                            add mary to items\nremove mary from items")
                       (library/get-initial-env extra-types))]
    (is (= (get (:ctx env) "items") (isla-list)))))

(deftest test-remove-obj-list
  (let [env (interpret (parse "mary is a person\nitems is a list
                            add mary to items\nremove mary from items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list)))))

(deftest test-remove-non-existent-item-does-nothing-string
  (let [env (interpret (parse "items is a list\nadd 'a' to items\nremove 'b' from items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list "a")))))

(deftest test-remove-non-existent-item-does-nothing-integer
  (let [env (interpret (parse "items is a list\nadd 1 to items\nremove 2 from items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env) (isla-list 1)))))

(deftest test-remove-non-existent-item-does-nothing-obj
  (let [env (interpret (parse "mary is a person\nmary age is 1
                            isla is a person\nitems is a list
                            add isla to items\nadd mary to items\nremove mary to items")
                       (library/get-initial-env extra-types))]
    (is (= (resolve- {:ref "items"} env)
           (isla-list ((get extra-types "person")))))))
