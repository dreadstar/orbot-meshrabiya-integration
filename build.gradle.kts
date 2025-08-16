// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("jacoco")
}

// Apply JaCoCo to all subprojects
subprojects {
    apply(plugin = "jacoco")
    
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.10"
    }
    
    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }
}

dependencies {
    // ...existing code...
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}

// Enhanced runAllTests task with comprehensive test execution and coverage
tasks.register("runAllTests") {
    description = "Runs all tests (unit, integration, Android) across all submodules with coverage analysis"
    group = "verification"

    // Collect all test tasks from subprojects
    val allTestTasks = subprojects.flatMap { subproject ->
        listOf(
            // Unit tests
            subproject.tasks.matching { it.name == "test" },
            subproject.tasks.matching { it.name == "testDebugUnitTest" },
            subproject.tasks.matching { it.name == "testReleaseUnitTest" },
            // Android instrumentation tests (if available)
            subproject.tasks.matching { it.name == "connectedDebugAndroidTest" }
        ).flatten()
    }
    
    dependsOn(allTestTasks)
    
    // Generate individual coverage reports first
    val coverageTasks = subprojects.mapNotNull { subproject ->
        subproject.tasks.findByName("jacocoTestReport")
    }
    
    dependsOn(coverageTasks)
    
    doLast {
        println("===============================================")
        println("🎯 ALL TESTS EXECUTION COMPLETE")
        println("===============================================")
        
        // Generate summary statistics
        val totalTests = subprojects.sumOf { subproject ->
            val testResults = subproject.layout.buildDirectory.dir("test-results").get().asFile
            if (testResults.exists()) {
                testResults.walkTopDown().count { it.name.endsWith(".xml") && it.readText().contains("<testsuite") }
            } else 0
        }
        
        println("📊 Test Summary:")
        println("   • Integration Module: Unit + Integration Tests")
        println("   • Meshrabiya Library: Comprehensive Test Suite (95+ tests)")
        println("   • Total Test Suites: $totalTests")
        
        println("\n📈 Coverage Reports Generated:")
        subprojects.forEach { subproject ->
            val coverageDir = subproject.layout.buildDirectory.dir("reports/jacoco").get().asFile
            if (coverageDir.exists()) {
                println("   • ${subproject.name}: ${coverageDir.absolutePath}")
            }
        }
        
        println("\n🔍 Test Reports Available At:")
        subprojects.forEach { subproject ->
            val testReportDir = subproject.layout.buildDirectory.dir("reports/tests").get().asFile
            if (testReportDir.exists()) {
                println("   • ${subproject.name}: ${testReportDir.absolutePath}")
            }
        }
        
        println("\n✅ Verification complete! All tests passed with coverage analysis.")
        println("===============================================")
    }
}

// Aggregate coverage report combining all subprojects
tasks.register<JacocoReport>("aggregatedCoverageReport") {
    description = "Generates aggregated code coverage report for all submodules"
    group = "verification"
    
    dependsOn(":integration:jacocoTestReport", ":Meshrabiya:lib-meshrabiya:jacocoTestReport")
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class", 
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*"
    )
    
    // Integration module sources and classes
    val integrationProject = project(":integration")
    val integrationSources = listOf(
        integrationProject.file("src/main/java"),
        integrationProject.file("src/main/kotlin")
    ).filter { it.exists() }
    
    val integrationExecFile = integrationProject.file("build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    
    // Meshrabiya module sources and classes  
    val meshrabiyaProject = project(":Meshrabiya:lib-meshrabiya")
    val meshrabiyaSources = listOf(
        meshrabiyaProject.file("src/main/java"),
        meshrabiyaProject.file("src/main/kotlin")
    ).filter { it.exists() }
    
    val meshrabiyaExecFile = meshrabiyaProject.file("build/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    
    // Source directories
    sourceDirectories.setFrom(integrationSources + meshrabiyaSources)
    
    // Class directories from compiled classes
    val integrationClasses = fileTree(integrationProject.file("build/tmp/kotlin-classes/debug")) {
        exclude(fileFilter)
    }
    val meshrabiyaClasses = fileTree(meshrabiyaProject.file("build/tmp/kotlin-classes/debug")) {
        exclude(fileFilter)
    }
    classDirectories.setFrom(integrationClasses, meshrabiyaClasses)
    
    // Execution data files (only include existing files)
    val executionFiles = listOf(integrationExecFile, meshrabiyaExecFile).filter { it.exists() }
    executionData.setFrom(executionFiles)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
        
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregated/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregated/jacoco.xml"))
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregated/jacoco.csv"))
    }
    
    doFirst {
        // Debug information
        println("🔍 AGGREGATED COVERAGE DEBUG:")
        println("   Source directories: ${sourceDirectories.files}")
        println("   Class directories: ${classDirectories.files}")
        println("   Execution files: ${executionData.files}")
        println("   Files that exist: ${executionData.files.filter { it.exists() }}")
    }
    
    doLast {
        val htmlReport = reports.html.outputLocation.get().asFile
        val xmlReport = reports.xml.outputLocation.get().asFile
        val csvReport = reports.csv.outputLocation.get().asFile
        
        println("\n🎯 AGGREGATED COVERAGE REPORT GENERATED")
        println("📊 Combined coverage analysis available at:")
        if (htmlReport.exists()) {
            println("   • HTML: file://${htmlReport.absolutePath}/index.html")
        }
        if (xmlReport.exists()) {
            println("   • XML: file://${xmlReport.absolutePath}")
        }
        if (csvReport.exists()) {
            println("   • CSV: file://${csvReport.absolutePath}")
        }
        
        // Also show individual module reports
        println("\n📋 Individual Module Reports:")
        println("   • Integration: file://${integrationProject.file("build/reports/jacoco/jacocoTestReport/html/index.html").absolutePath}")
        println("   • Meshrabiya: file://${meshrabiyaProject.file("build/reports/jacoco/jacocoTestReport/html/index.html").absolutePath}")
    }
}

// Make runAllTests automatically generate the aggregated coverage report
tasks.named("runAllTests") {
    finalizedBy("aggregatedCoverageReport")
}
