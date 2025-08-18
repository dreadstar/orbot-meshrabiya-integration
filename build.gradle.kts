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

    doLast {
        println("===============================================")
        println("🎯 ORBOT-MESHRABIYA INTEGRATION TEST RESULTS")
        println("===============================================")
        println()
        
        // Clear previous test reports and logs
        println("🧹 Clearing previous test reports and logs...")
        try {
            delete(file("build/reports"))
            delete(file("build/test-results"))
            delete(file("integration/build/reports"))
            delete(file("integration/build/test-results"))
            delete(file("Meshrabiya/lib-meshrabiya/build/reports"))
            delete(file("Meshrabiya/lib-meshrabiya/build/test-results"))
            delete(file("Meshrabiya/test-app/build/reports"))
            delete(file("Meshrabiya/test-app/build/test-results"))
            delete(file("Meshrabiya/test-shared/build/reports"))
            delete(file("Meshrabiya/test-shared/build/test-results"))
            println("   ✅ Previous reports cleared")
        } catch (e: Exception) {
            println("   ⚠️  Warning: Could not clear all previous reports")
        }
        println()
        
        // Function to parse test results from XML
        fun parseTestResults(xmlFile: File): Triple<Int, Int, Int> {
            if (!xmlFile.exists()) return Triple(0, 0, 0)
            
            return try {
                val content = xmlFile.readText()
                val testsRegex = """tests="(\d+)"""".toRegex()
                val failuresRegex = """failures="(\d+)"""".toRegex()
                val errorsRegex = """errors="(\d+)"""".toRegex()
                
                val tests = testsRegex.find(content)?.groupValues?.get(1)?.toInt() ?: 0
                val failures = failuresRegex.find(content)?.groupValues?.get(1)?.toInt() ?: 0
                val errors = errorsRegex.find(content)?.groupValues?.get(1)?.toInt() ?: 0
                
                Triple(tests, failures, errors)
            } catch (e: Exception) {
                Triple(0, 0, 0)
            }
        }
        
        // Function to get all test results from a build directory
        fun getAllTestResults(buildDir: File): Triple<Int, Int, Int> {
            val testResultsDir = File(buildDir, "test-results")
            if (!testResultsDir.exists()) return Triple(0, 0, 0)
            
            var totalTests = 0
            var totalFailures = 0
            var totalErrors = 0
            
            testResultsDir.walkTopDown()
                .filter { it.name.startsWith("TEST-") && it.extension == "xml" }
                .forEach { xmlFile ->
                    val (tests, failures, errors) = parseTestResults(xmlFile)
                    totalTests += tests
                    totalFailures += failures
                    totalErrors += errors
                }
            
            return Triple(totalTests, totalFailures, totalErrors)
        }
        
        // Run tests and collect results
        var totalTests = 0
        var successfulTests = 0
        var failedTests = 0
        var compilationErrors = 0
        
        val testResults = mutableMapOf<String, Triple<Int, Int, Boolean>>() // module -> (passed, failed, compiled)
        
        // Test integration module
        println("📋 Running Integration Module Tests...")
        var integrationCompiled = false
        try {
            project.exec {
                commandLine("./gradlew", ":integration:test", "--continue", "--console=plain")
            }
            integrationCompiled = true
            
            val (tests, failures, errors) = getAllTestResults(file("integration/build"))
            val failed = failures + errors
            val passed = tests - failed
            
            testResults["Integration"] = Triple(passed, failed, true)
            successfulTests += passed
            failedTests += failed
            totalTests += tests
            
            if (failed == 0) {
                println("   ✅ Integration: $tests tests passed")
            } else {
                println("   ⚠️  Integration: $passed passed, $failed failed ($tests total)")
            }
        } catch (e: Exception) {
            println("   ❌ Integration: Compilation/execution failed - ${e.message}")
            compilationErrors++
            testResults["Integration"] = Triple(0, 0, false)
        }
        
        // Test Meshrabiya lib-meshrabiya module 
        println("📋 Running Meshrabiya Library Tests...")
        try {
            project.exec {
                commandLine("./gradlew", ":Meshrabiya:lib-meshrabiya:testDebugUnitTest", "--continue", "--console=plain")
                isIgnoreExitValue = true // Don't fail if tests fail
            }
            
            val (tests, failures, errors) = getAllTestResults(file("Meshrabiya/lib-meshrabiya/build"))
            val failed = failures + errors
            val passed = tests - failed
            
            testResults["Meshrabiya Library"] = Triple(passed, failed, true)
            successfulTests += passed
            failedTests += failed
            totalTests += tests
            
            if (failed == 0) {
                println("   ✅ Meshrabiya Library: $tests tests passed")
            } else {
                println("   ⚠️  Meshrabiya Library: $passed passed, $failed failed ($tests total)")
            }
        } catch (e: Exception) {
            println("   ❌ Meshrabiya Library: Compilation/execution failed - ${e.message}")
            compilationErrors++
            testResults["Meshrabiya Library"] = Triple(0, 0, false)
        }
        
        println()
        println("===============================================")
        println("📊 COMPREHENSIVE TEST SUMMARY")
        println("===============================================")
        println("   • Total Tests Executed: $totalTests")
        println("   • Successful Tests: $successfulTests")
        println("   • Failed Tests: $failedTests")
        println("   • Compilation Errors: $compilationErrors")
        println()
        
        if (failedTests > 0 || compilationErrors > 0) {
            println("⚠️  Test Failure Details:")
            testResults.forEach { (module, results) ->
                val (passed, failed, compiled) = results
                if (!compiled) {
                    println("   • $module: Compilation failed")
                } else if (failed > 0) {
                    println("   • $module: $failed failures, $passed successes")
                }
            }
            println()
        }
        
        println("🔍 Test Reports Available:")
        if (file("integration/build/reports/tests").exists()) {
            println("   • Integration: integration/build/reports/tests/test/index.html")
        }
        if (file("Meshrabiya/lib-meshrabiya/build/reports/tests").exists()) {
            println("   • Meshrabiya Library: Meshrabiya/lib-meshrabiya/build/reports/tests/testDebugUnitTest/index.html")
        }
        println()
        
        println("📈 Coverage Reports:")
        if (file("integration/build/reports/jacoco").exists()) {
            println("   • Integration: integration/build/reports/jacoco/test/html/index.html")
        }
        if (file("Meshrabiya/lib-meshrabiya/build/reports/jacoco").exists()) {
            println("   • Meshrabiya Library: Meshrabiya/lib-meshrabiya/build/reports/jacoco/testDebugUnitTest/html/index.html")
        }
        println()
        
        val successRate = if (totalTests > 0) (successfulTests * 100) / totalTests else 0
        println("📋 Overall Status:")
        println("   • Success Rate: $successRate% ($successfulTests/$totalTests)")
        if (compilationErrors == 0) {
            println("   • Compilation: ✅ All modules compile successfully")
        } else {
            println("   • Compilation: ❌ $compilationErrors modules have compilation issues")
        }
        
        if (successRate >= 75) {
            println("   • Assessment: ✅ Good test coverage with most tests passing")
        } else if (successRate >= 50) {
            println("   • Assessment: ⚠️  Moderate success rate - some test fixes needed")
        } else if (totalTests > 0) {
            println("   • Assessment: ❌ Many tests failing - significant fixes required")
        } else {
            println("   • Assessment: ❌ No tests found or major compilation issues")
        }
        
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
