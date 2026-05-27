plugins {
    id("com.google.protobuf") version "0.9.4"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.protobuf:protobuf-java:3.25.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
