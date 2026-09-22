# Executor contract (pi adds this to every dispatch)

Obey the attached task spec. This contract controls how you work.

- **Scope:** change only the files that the spec lists in *Files in scope*.
  Change no other file.
- **Engine:** do not change the C++ sources in `MicropolisCore/`. The one
  exception is a file that the spec names.
- **Interface:** copy the signatures, declarations, and skeletons in the spec
  exactly. Do not change the boundary.
- **The Definition of Done is the gate:** run the build command and the test. Do
  the work again until the test is green. Do not report success if the test is
  not green.
- **If the work fails:** stop. Give the exact command, the exact error text, and
  your best diagnosis. Do not invent a success result.
- **Dependencies:** do not add a new dependency. The one exception is a
  dependency that the spec permits.
- **Small changes:** the planner reviews every change before it goes into the code.
- **Commit when green:** when the test passes, commit your work with git. Write a
  short message that tells what you did. Make these commits small and frequent.
  They give safe points to go back to. The planner squashes them later.
