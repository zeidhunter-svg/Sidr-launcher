# Next Steps

1. Add domain contracts:
   - AI request/response models
   - intent/action models
   - repository interfaces
   - use-case stubs

2. Add data-layer stubs:
   - cloud AI Ktor adapter placeholder
   - local ONNX classifier placeholder
   - local LLM interface placeholder
   - safe fake implementations for build-time wiring

3. Add DI placeholders:
   - Hilt module bindings for stub implementations
   - dispatcher/provider bindings if needed

4. Add feature placeholders:
   - launcher home screen composable
   - assistant command input composable
   - suggestions placeholder composable

5. Verify after each step:
   - run `./gradlew assembleDebug`
   - keep skeleton compile-ready before adding business logic

6. After contracts and stubs compile, implement launcher shell and local intent matching.
