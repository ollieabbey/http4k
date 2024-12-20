import org.http4k.internal.ModuleLicense.Http4kCommunity

description = "http4k Tracer Bullet module"

val license by project.extra { Http4kCommunity }

plugins {
    id("org.http4k.community")
}

dependencies {
    api(project(":http4k-core"))
    api(project(":http4k-format-moshi"))
    api(Square.moshi.adapters)
    compileOnly("org.junit.jupiter:junit-jupiter-api:_")

    testImplementation(project(":http4k-testing-strikt"))
    testImplementation(project(":http4k-client-apache"))
    testImplementation(testFixtures(project(":http4k-core")))
    testImplementation(project(path = ":http4k-testing-approval"))
    testImplementation(testFixtures(project(":http4k-contract")))
}
