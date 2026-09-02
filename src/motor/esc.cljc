(ns motor.esc
  "ESC (inverter) feasibility of one torque-speed operating point against a DC
  bus (:esc-feasibility).

  This is the missing link between the bus plane and the mechanical planes of
  this library: `motor.dcbus` treats the traction load as an already-net kW
  number and explicitly leaves converter losses/limits to another plane;
  `motor.solver` / `motor.envelope` speak torque and speed. This contract takes
  ONE operating point (torque, speed) and the machine + ESC electrical
  parameters, and answers: can this ESC deliver this torque at this speed from
  this bus?

  No physical constant is invented here. Everything that sets a limit or a
  gain is REQUIRED from the caller:

    :torque-Nm       demanded shaft torque, N·m (>= 0; braking is out of scope)
    :rpm             shaft speed, rpm (>= 0)
    :kt-Nm-per-A     motor torque constant, N·m per ampere
    :ke-Vs-per-rad   motor back-EMF constant, V·s per rad/s. NOT assumed equal
                     to :kt-Nm-per-A: phase conventions (RMS vs peak, phase vs
                     line) make the identity convention-dependent, so the
                     caller supplies the constant in the same convention used
                     for :r-phase-ohm and :v-ceiling-V.
    :r-phase-ohm     winding resistance seen by the ESC (same convention)
    :i-max-A         ESC current limit, A
    :v-ceiling-V     maximum voltage magnitude the ESC can present to the
                     winding from this bus, V. The modulation strategy (SVPWM,
                     six-step, ...) is the caller's decision — this contract
                     does not assume sqrt(3)·Vdc or any modulation factor.

  Model: quasi-static DC-equivalent. i = T/kt; back-EMF e = ke·omega; the
  voltage the ESC must present is v = e + i·R; electrical input power is
  e·i + i²R; mechanical power is T·omega. Copper loss is the only electrical
  loss modeled. Iron loss, mechanical (friction/windage) loss, switching loss,
  and ESC thermal capability are real and unmeasured here — the result marks
  them `:unmeasured` so a consumer cannot read the reported efficiency as a
  measured one."
  (:require [cae.solver :as cae]))

(defn- require-num
  [m key & [allow-zero?]]
  (let [v (get m key)]
    (when-not (and (number? v)
                   (if allow-zero? (>= v 0) (pos? v)))
      (throw (ex-info (str "esc needs a positive number for " (name key)
                           (when allow-zero? " (zero allowed)"))
                      {:key key :value v})))
    (double v)))

(defn solve
  "Feasibility of one operating point. See the namespace docstring for the
  required case keys — none have defaults, by design.

  Returns:
    :omega-rad-s    shaft speed, rad/s
    :i-A            phase current magnitude implied by the torque demand, A
    :back-emf-V     back-EMF magnitude at :rpm, V (caller's convention)
    :v-required-V   voltage the ESC must present, V (= e + i·R)
    :p-mech-kW      shaft power, kW
    :p-elec-kW      electrical input power (e·i + i²R), kW
    :copper-loss-kW i²R, kW
    :eff            p-mech/p-elec (nil when p-elec is zero)
    :current-ok?    i <= :i-max-A
    :voltage-ok?    v-required <= :v-ceiling-V
    :feasible?      both above
    :headroom-A     :i-max-A - :i-A (may be negative)
    :headroom-V     :v-ceiling-V - :v-required-V (may be negative)
    :unmeasured     losses this model does not capture
    :solver         :esc-feasibility"
  [case]
  (let [t   (require-num case :torque-Nm true)
        rpm (require-num case :rpm true)
        kt  (require-num case :kt-Nm-per-A)
        ke  (require-num case :ke-Vs-per-rad)
        r   (require-num case :r-phase-ohm)
        imax (require-num case :i-max-A)
        vceil (require-num case :v-ceiling-V)
        omega (* rpm (/ (* 2.0 Math/PI) 60.0))
        i (/ t kt)
        e (* ke omega)
        v-req (+ e (* i r))
        p-mech (* t omega)
        cu (* i i r)
        p-elec (+ (* e i) cu)
        eff (when (pos? p-elec) (/ p-mech p-elec))
        i-ok (<= i imax)
        v-ok (<= v-req vceil)]
    {:omega-rad-s omega
     :i-A i
     :back-emf-V e
     :v-required-V v-req
     :p-mech-kW (/ p-mech 1000.0)
     :p-elec-kW (/ p-elec 1000.0)
     :copper-loss-kW (/ cu 1000.0)
     :eff eff
     :current-ok? i-ok
     :voltage-ok? v-ok
     :feasible? (and i-ok v-ok)
     :headroom-A (- imax i)
     :headroom-V (- vceil v-req)
     :unmeasured [:iron-loss :friction-windage-loss :switching-loss
                  :esc-thermal-derating :regeneration-braking]
     :solver :esc-feasibility}))

(defmethod cae/solve :esc-feasibility [case] (solve case))
