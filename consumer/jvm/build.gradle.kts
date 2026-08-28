plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// :domain is the ONLY project dependency, and that is the point of this module (spec §4). No core/*,
// no data/*, no feature/*, nothing Android. `ModuleIsolationTest` holds it.
dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}

/**
 * Makes the command `Main.kt` documents actually exist:
 * `./gradlew :consumer:jvm:run --args="<sandbox dir> remove <file name>"`.
 *
 * A hand-registered `JavaExec`, deliberately **not** the `application` plugin. Distribution and
 * packaging are named non-goals of this block, and `application` would bring `distZip`, `distTar`,
 * `installDist` and a start-script model with them — a desktop app's shape, for a module whose whole
 * claim is that it is a console harness and not a product. `JavaExec` declares `--args` itself, so the
 * documented invocation works verbatim without it.
 *
 * `standardInput` is wired to the real one because the harness *asks*: its consent gate reads a line
 * from stdin, and a JavaExec left at Gradle's default would hand it an empty stream — every run would
 * take the "no answer given" branch and no `y` could ever be typed.
 */
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the console harness: --args=\"<sandbox dir> remove <file name>\""
    mainClass.set("com.sidr.launcher.consumer.jvm.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
