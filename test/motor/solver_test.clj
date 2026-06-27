(ns motor.solver-test
  (:require [clojure.test :refer [deftest is testing]]
            [motor.solver :as motor]
            [cae.solver :as cae]))

(deftest realistic-traction-motor
  (testing "a ~200 mm rotor lands in plausible power/efficiency/torque-density"
    (let [r (motor/solve {})]
      (is (< 60 (:p-peak-kW r) 220) (str "P=" (:p-peak-kW r) " kW"))
      (is (< 0.92 (:eff-peak r) 0.98))
      (is (< 1.0 (:Nm-per-kg r) 12.0)))))

(deftest stronger-magnet-more-torque
  (testing "higher remanence raises torque (shear ∝ Br)"
    (is (< (:torque-Nm (motor/solve {:magnet :ferrite}))
           (:torque-Nm (motor/solve {:magnet :NdFeB-N48}))))))

(deftest bigger-rotor-more-torque
  (testing "torque ∝ D²·L"
    (is (< (:torque-Nm (motor/solve {:d-rotor-mm 180}))
           (:torque-Nm (motor/solve {:d-rotor-mm 240}))))))

(deftest registered-on-contract
  (is (cae/registered? :rom-motor))
  (is (= :rom-motor (:solver (cae/solve {:solver {:kind :rom-motor}})))))

(deftest datafied
  (is (pos? (:datom-count (motor/run {:case/id "sedan/motor"})))))
