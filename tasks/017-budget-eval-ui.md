# Task 017: Budget + Evaluation popup windows

## Goal
Add a "Budget" button and an "Eval" button that open dialogs showing the city
budget and the mayor's evaluation.

## Context
Task 016 added `MicropolisNative.getBudget(handle, IntArray(12))` and
`getEvaluation(handle, IntArray(7))`. Their index layouts:
- budget: `[totalFunds, taxRate, taxIncome, roadFund, roadSpend, roadPct,
  policeFund, policeSpend, policePct, fireFund, fireSpend, firePct]`
- eval: `[score, scoreDelta, cityClass, pop, popDelta, assessedValue, approval]`

**Threading:** the engine reads run on the `sim` thread; a dialog shows on the UI
thread. So on click: post the read to `sim`, then `ui.post { showDialog(...) }`.
Use `androidx.appcompat.app.AlertDialog`.

## Interface (implementation notes for MainActivity.kt)
- Add two buttons to `barLayout` (after the Tax button):
  ```kotlin
  val budgetBtn = Button(this).apply {
      text = "Budget"
      setOnClickListener {
          sim.post {
              val b = IntArray(12); MicropolisNative.getBudget(handle, b)
              ui.post { showBudgetDialog(b) }
          }
      }
  }
  val evalBtn = Button(this).apply {
      text = "Eval"
      setOnClickListener {
          sim.post {
              val ev = IntArray(7); MicropolisNative.getEvaluation(handle, ev)
              ui.post { showEvalDialog(ev) }
          }
      }
  }
  barLayout.addView(budgetBtn); barLayout.addView(evalBtn)
  ```
- Add the two dialog helpers (member functions):
  ```kotlin
  private fun showBudgetDialog(b: IntArray) {
      val msg = """
          Funds: $${b[0]}
          Tax rate: ${b[1]}%     Tax income: $${b[2]}

          Roads:  $${b[4]} / $${b[3]}   (${b[5]}%)
          Police: $${b[7]} / $${b[6]}   (${b[8]}%)
          Fire:   $${b[10]} / $${b[9]}   (${b[11]}%)
      """.trimIndent()
      androidx.appcompat.app.AlertDialog.Builder(this)
          .setTitle("City Budget").setMessage(msg)
          .setPositiveButton("OK", null).show()
  }

  private fun showEvalDialog(ev: IntArray) {
      val classes = arrayOf("Village","Town","City","Capital","Metropolis","Megalopolis")
      val cls = classes.getOrElse(ev[2]) { "?" }
      val msg = """
          Score: ${ev[0]}  (Δ ${ev[1]})
          Class: $cls
          Population: ${ev[3]}  (Δ ${ev[4]})
          Assessed value: $${ev[5]}
          Approval: ${ev[6]}%
      """.trimIndent()
      androidx.appcompat.app.AlertDialog.Builder(this)
          .setTitle("City Evaluation").setMessage(msg)
          .setPositiveButton("OK", null).show()
  }
  ```

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.

## Constraints
- Change only `MainActivity.kt`.
- Do not change the JNI, the C ABI, the CMake files, `build.gradle.kts`, the engine,
  or `MapView`.
- No new dependencies (androidx.appcompat is already a dependency). Commit when
  green; stage only your file by name. Do not use `git checkout`, `git reset`, or
  `git add -A`.
