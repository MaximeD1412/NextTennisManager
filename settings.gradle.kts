rootProject.name = "NextTennisManager"

include(":shared:proto")
include(":simulator")
include(":webapp:backend")

project(":simulator").projectDir = file("NextManagerTennis_Simulator")
project(":webapp").projectDir = file("NextManagerTennis_WebApp")
project(":webapp:backend").projectDir = file("NextManagerTennis_WebApp/backend")
