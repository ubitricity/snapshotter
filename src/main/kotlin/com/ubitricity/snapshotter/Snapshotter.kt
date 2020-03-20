/**
 * Copyright (c), ubitricity Gesellschaft für Verteilte Energiesysteme mbH,
 * Berlin, Germany
 *
 * All rights reserved. Dissemination, reproduction, or use of this material in source
 * and binary forms requires prior written permission from ubitricity.
 */
package com.ubitricity.snapshotter

import com.fasterxml.jackson.databind.ObjectMapper
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.nio.file.Paths
import kotlin.test.assertEquals

/**
 * Interface for Snapshotter serialization module.
 * Can be used to change the way snapshots are serialized to String,
 * for example, to provide custom Jackson ObjectMapper.
 */
interface SnapshotSerializer {
    fun serialize(data: Any?): String
}

/**
 * Allows customizing JSON compare mode.
 * Maps directly to [JSONCompareMode].
 * Useful to change the default behavior when order matters (e.g. when testing sorting)
 *
 * By default, will translate to [JSONCompareMode.NON_EXTENSIBLE],
 * that is the objects cannot have extra properties,
 * but the order does not matter.
 */
data class CompareMode(
        val extensible: Boolean = false,
        val strictOrder: Boolean = false
)

/**
 * Describes additional config for Snapshot validation.
 * `snapshotName` - Can be used to override the name of snapshot file.
 * If left as `null`, the test case name will be extracted and used as filename.
 * `compareMode` - See [CompareMode]
 * `ignore` - Contains a list of fields that should be ignored when
 * comparing snapshot, for example: ids
 * `only` - Contains a list of fields that should be validated when comparing
 * snapshots. Only those fields will be checked, other ones will be ignored.
 * Both lists can contain String fields identifiers, but wildcards are also allowed:
 * - "id" Will only match top-level id field
 * - "foo.id" Will match nested foo.id field
 * - "**.id" Will match all id field, no matter the nesting level
 * - "***" Will match all fields
 */
data class ValidationConfig(
        val snapshotName: String? = null,
        val compareMode: CompareMode = CompareMode(),
        val ignore: Collection<String> = emptyList(),
        val only: Collection<String> = emptyList()
) {
    /**
     * Custom Builder for better Java interop.
     * For pure Kotlin usage, a constructor with named parameters is preferred.
     * Example from Java:
     * new ValidationConfig.Builder()
     *     .ignore("**.id", "**.timestamp") // Notice the vararg parameters
     *     .build()
     */
    class Builder {
        private var _snapshotName: String? = null
        private var _compareMode: CompareMode = CompareMode()
        private var _ignore: Collection<String> = emptyList()
        private var _only: Collection<String> = emptyList()

        fun snapshotName(snapshotName: String?) = apply {
            this._snapshotName = snapshotName
        }

        fun compareMode(compareMode: CompareMode) = apply {
            this._compareMode = compareMode
        }

        fun ignore(vararg ignore: String) = apply {
            this._ignore = listOf(*ignore)
        }

        fun only(vararg only: String) = apply {
            this._only = listOf(*only)
        }

        fun build(): ValidationConfig {
            return ValidationConfig(
                    snapshotName = _snapshotName,
                    compareMode = _compareMode,
                    ignore = _ignore,
                    only = _only
            )
        }
    }
}

/**
 * Snapshotter module to perform snapshot testing.
 * This is heavily inspired by: https://github.com/Karumi/KotlinSnapshot
 * However, this module will compare the snapshots as JSON objects instead of Strings.
 * This has the advantage of:
 * - Being able to disregard order in JSON arrays and objects
 * - Being able to pass in additional config for ignoring fields
 *
 * Use `snapshotsDirectory` to override the default location where snapshots files should be stored
 * Use `snapshotSerializer` to provide custom serialization module
 */
class Snapshotter @JvmOverloads constructor(
        private val snapshotsDirectory: String = "__snapshots__",
        private val snapshotSerializer: SnapshotSerializer = DefaultSnapshotSerializer()
) {
    private val testCaseExtractor = TestCaseExtractor()
    private val objectMapper = ObjectMapper()

    @JvmOverloads
    fun validateSnapshot(
            data: Any?,
            validationConfig: ValidationConfig = ValidationConfig()
    ) {
        val dataString = snapshotSerializer.serialize(data)
        val testCase = testCaseExtractor.extractTestCase()

        val snapshotName = if (validationConfig.snapshotName != null) {
            TestCase(testCase.className, validationConfig.snapshotName)
        } else {
            testCase
        }

        val snapshotFile = getSnapshotFile(snapshotName)
        if (!snapshotFile.exists() || System.getProperty("updateSnapshots") == "1") {
            snapshotFile.writeText(dataString)
        } else {
            val savedSnapshotContent = snapshotFile.readText()
            if (data != null && isValidJson(dataString)) {
                // Perform JSON assertion
                JSONAssert.assertEquals(
                        savedSnapshotContent,
                        dataString,
                        buildComparator(validationConfig)
                )
            } else {
                // Perform primitive assertion
                assertEquals(savedSnapshotContent, dataString)
            }
        }
    }

    private fun isValidJson(data: String?): Boolean {
        return try {
            objectMapper.readTree(data)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun buildComparator(validationConfig: ValidationConfig): CustomComparator {
        val ignoreCustomizations = validationConfig.ignore.map {
            Customization(it) { _, _ -> true }
        }

        val onlyCustomizations = if (validationConfig.only.isNotEmpty()) {
            validationConfig.only.map {
                Customization(it) { o1, o2 -> o1 == o2 }
            }.plus(
                    Customization("***") { _, _ -> true }
            )
        } else {
            emptyList()
        }

        val customizations = listOf(*ignoreCustomizations.toTypedArray(), *onlyCustomizations.toTypedArray())

        return CustomComparator(
                JSONCompareMode.NON_EXTENSIBLE
                        .withExtensible(validationConfig.compareMode.extensible)
                        .withStrictOrdering(validationConfig.compareMode.strictOrder),
                *customizations.toTypedArray()
        )
    }

    private fun getSnapshotFile(testCase: TestCase): File {
        return Paths.get(
                snapshotsDirectory,
                testCase.className,
                "${testCase.methodName}.snap"
        ).toFile().also {
            it.parentFile.mkdirs()
        }
    }
}

private class DefaultSnapshotSerializer : SnapshotSerializer {
    private val objectMapper: ObjectMapper = ObjectMapper()

    override fun serialize(data: Any?): String {
        return when (data) {
            is String -> data
                    .plus(System.lineSeparator())
            else -> objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data)
                    .plus(System.lineSeparator())
        }
    }
}

private data class TestCase(
        val className: String,
        val methodName: String
)

private class TestCaseExtractor {
    fun extractTestCase(): TestCase {
        val stackTraceElement = getTestStackElement()
                ?: throw RuntimeException("Could not resolve test case name.")
        return TestCase(stackTraceElement.className, stackTraceElement.methodName)
    }

    private fun getTestStackElement(): StackTraceElement? {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace.toList().firstOrNull { trace ->
            try {
                val traceClass = Class.forName(trace.className)
                val method = traceClass.getMethod(trace.methodName)
                isTestMethod(method)
            } catch (exception: Exception) {
                false
            }
        }
    }

    private fun isTestMethod(method: Method): Boolean =
            method.annotations.any {
                it.annotationClass.qualifiedName?.matches(TEST_ANNOTATION_EXPRESSION) ?: false
            }

    companion object {
        private val TEST_ANNOTATION_EXPRESSION = Regex("(.*).[T|t]est")
    }
}
